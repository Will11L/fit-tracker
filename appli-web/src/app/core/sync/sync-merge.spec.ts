import { Observable, of } from 'rxjs';
import { isRemoteNewer, mergeFromRemote, pushAllRows, pushStore, replaceFromRemote } from './sync-merge';
import { SyncStats, SyncableStore, SyncRow, computeStats } from './syncable-store';

interface TestRow extends SyncRow {
  name: string;
}

/** Store en mémoire (sans Dexie) pour tester la logique de sync pure. */
class FakeStore implements SyncableStore<TestRow> {
  readonly name = 'test';
  readonly wsKey = 'test';
  pushedUpserts: string[] = [];
  pushedBulks: string[][] = [];
  pushedDeletes: string[] = [];
  /** Simule un serveur sans endpoint bulk (force le fallback 1-par-1). */
  bulkFails = false;
  constructor(
    public local: TestRow[],
    public remote: TestRow[],
  ) {}

  getAllLocal(): Promise<TestRow[]> {
    return Promise.resolve(this.local.map((r) => ({ ...r })));
  }
  async bulkPutLocal(rows: TestRow[]): Promise<void> {
    for (const r of rows) {
      const i = this.local.findIndex((x) => x.uuid === r.uuid);
      if (i >= 0) this.local[i] = r;
      else this.local.push(r);
    }
  }
  async bulkDeleteLocal(uuids: string[]): Promise<void> {
    this.local = this.local.filter((x) => !uuids.includes(x.uuid));
  }
  async markSyncedLocal(uuid: string): Promise<void> {
    const r = this.local.find((x) => x.uuid === uuid);
    if (r) {
      r.synced = true;
      r.pendingDeletion = false;
    }
  }
  async deleteLocal(uuid: string): Promise<void> {
    this.local = this.local.filter((x) => x.uuid !== uuid);
  }
  async clearLocal(): Promise<void> {
    this.local = [];
  }
  liveStats(): Observable<SyncStats> {
    return of(computeStats(this.name, this.local));
  }
  fetchRemote(): Promise<TestRow[]> {
    return Promise.resolve(this.remote.map((r) => ({ ...r, synced: true, pendingDeletion: false })));
  }
  async pushUpsert(row: TestRow): Promise<void> {
    this.pushedUpserts.push(row.uuid);
  }
  async pushUpsertBulk(rows: TestRow[]): Promise<void> {
    if (this.bulkFails) throw new Error('bulk endpoint failed');
    this.pushedBulks.push(rows.map((r) => r.uuid));
  }
  async pushDelete(row: TestRow): Promise<void> {
    this.pushedDeletes.push(row.uuid);
  }
}

function row(uuid: string, opts: Partial<TestRow> = {}): TestRow {
  return {
    uuid,
    name: uuid,
    updatedAt: '2026-01-01T00:00:00.000Z',
    synced: true,
    pendingDeletion: false,
    ...opts,
  };
}

describe('isRemoteNewer', () => {
  it('remote plus récent => true', () => {
    expect(isRemoteNewer('2026-01-01T00:00:00Z', '2026-01-02T00:00:00Z')).toBe(true);
  });
  it('remote plus ancien => false', () => {
    expect(isRemoteNewer('2026-01-02T00:00:00Z', '2026-01-01T00:00:00Z')).toBe(false);
  });
  it('local null => true', () => {
    expect(isRemoteNewer(null, '2026-01-01T00:00:00Z')).toBe(true);
  });
  it('remote null => false', () => {
    expect(isRemoteNewer('2026-01-01T00:00:00Z', null)).toBe(false);
  });
});

describe('computeStats', () => {
  it('catégorise synced / unsynced / pendingDeletion', () => {
    const s = computeStats('t', [
      row('1', { synced: true }),
      row('2', { synced: false }),
      row('3', { synced: true, pendingDeletion: true }),
    ]);
    expect(s).toEqual({ name: 't', total: 3, synced: 1, unsynced: 1, pendingDeletion: 1 });
  });
});

describe('mergeFromRemote — 3 contrats SYNC_PATTERN', () => {
  it('Contrat 3 : prune un local synced absent du remote', async () => {
    const store = new FakeStore([row('1'), row('2'), row('3')], [row('1'), row('3')]);
    await mergeFromRemote(store);
    expect(store.local.map((r) => r.uuid).sort()).toEqual(['1', '3']);
  });

  it('Contrat 2 : préserve un local NON syncé absent du remote (création locale)', async () => {
    const store = new FakeStore([row('local-new', { synced: false })], []);
    await mergeFromRemote(store);
    expect(store.local.map((r) => r.uuid)).toEqual(['local-new']);
  });

  it('insère les rows remote inconnues en synced=true', async () => {
    const store = new FakeStore([], [row('1')]);
    await mergeFromRemote(store);
    expect(store.local).toHaveLength(1);
    expect(store.local[0].synced).toBe(true);
  });

  it('last-write-wins : remote plus récent écrase le local', async () => {
    const store = new FakeStore(
      [row('1', { name: 'old', updatedAt: '2026-01-01T00:00:00.000Z' })],
      [row('1', { name: 'new', updatedAt: '2026-02-01T00:00:00.000Z' })],
    );
    await mergeFromRemote(store);
    expect(store.local[0].name).toBe('new');
  });

  it('last-write-wins : remote plus ancien ne touche pas le local', async () => {
    const store = new FakeStore(
      [row('1', { name: 'local-recent', updatedAt: '2026-03-01T00:00:00.000Z' })],
      [row('1', { name: 'remote-old', updatedAt: '2026-01-01T00:00:00.000Z' })],
    );
    await mergeFromRemote(store);
    expect(store.local[0].name).toBe('local-recent');
  });
});

describe('pushAllRows', () => {
  it('upsert (bulk) toutes les rows non-supprimées et delete les pendingDeletion', async () => {
    const store = new FakeStore(
      [row('1', { synced: true }), row('2', { synced: false }), row('3', { pendingDeletion: true })],
      [],
    );
    await pushAllRows(store);
    expect(store.pushedBulks).toEqual([['1', '2']]); // 1 seule requête bulk
    expect(store.pushedUpserts).toEqual([]);
    expect(store.pushedDeletes).toEqual(['3']);
    expect(store.local.map((r) => r.uuid).sort()).toEqual(['1', '2']); // 3 supprimée localement
  });
});

describe('pushStore', () => {
  it('ne pousse que les rows unsynced, en 1 requête bulk, et les marque synced', async () => {
    const store = new FakeStore([row('1', { synced: true }), row('2', { synced: false })], []);
    await pushStore(store);
    expect(store.pushedBulks).toEqual([['2']]);
    expect(store.local.every((r) => r.synced)).toBe(true);
  });

  it('fallback 1-par-1 si le bulk échoue (miroir EntitySyncUtils Android)', async () => {
    const store = new FakeStore([row('1', { synced: false }), row('2', { synced: false })], []);
    store.bulkFails = true;
    await pushStore(store);
    expect(store.pushedUpserts.sort()).toEqual(['1', '2']);
    expect(store.local.every((r) => r.synced)).toBe(true);
  });
});

describe('replaceFromRemote', () => {
  it('vide le local et ré-insère le remote en synced=true', async () => {
    const store = new FakeStore([row('old-local', { synced: false })], [row('a'), row('b')]);
    await replaceFromRemote(store, true);
    expect(store.local.map((r) => r.uuid).sort()).toEqual(['a', 'b']);
    expect(store.local.every((r) => r.synced)).toBe(true);
  });
});
