import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Data table — tableau de données générique (colonnes + lignes d'objets). Cellules typées
 * automatiquement : booléen → marqueur coloré ✓ / –, nombre → centré, date ISO → raccourcie
 * (AAAA-MM-JJ HH:MM), colonne *uuid* → monospace, null/undefined → « — ». En-tête sticky,
 * zebra une ligne sur deux, scroll horizontal. Extrait de Sync Settings pour être réutilisable
 * et discutable au design system. Purement présentation (pagination/sélection gérées par le parent).
 */
@Component({
  selector: 'app-data-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="data__scroll">
      <table class="dt">
        <thead>
          <tr>
            @for (c of columns(); track c) {
              <th>{{ c }}</th>
            }
          </tr>
        </thead>
        <tbody>
          @for (row of rows(); track $index) {
            <tr>
              @for (c of columns(); track c) {
                @let cc = cell(c, row[c]);
                <td [class]="cc.cls" [title]="cc.text">{{ cc.text }}</td>
              }
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .data__scroll {
        overflow-x: auto;
      }
      .dt {
        border-collapse: collapse;
        width: 100%;
        font-size: var(--font-size-caption);
      }
      .dt th,
      .dt td {
        text-align: left;
        padding: 6px var(--space-2);
        font-size: 11px;
        white-space: nowrap;
        max-width: 260px;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .dt th {
        color: var(--app-text-primary);
        font-weight: 600;
        position: sticky;
        top: 0;
        background: var(--app-bg-surface);
      }
      .dt td {
        color: var(--app-text-primary);
      }
      /* zebra (= DataRow.kt : lignes paires bgSurface @ 0.4) */
      .dt tbody tr:nth-child(even) td {
        background: color-mix(in srgb, var(--app-bg-surface) 40%, transparent);
      }
      .dt .dt--mono {
        font-family: monospace;
      }
      .dt .dt--num {
        text-align: center;
      }
      .dt .dt--true {
        text-align: center;
        font-weight: 700;
        color: var(--c-medium-green);
      }
      .dt .dt--false {
        text-align: center;
        color: var(--app-primary-action);
      }
      .dt .dt--null {
        text-align: center;
        color: var(--app-text-tertiary);
      }
      .dt .dt--date {
        color: var(--app-text-secondary);
      }
    `,
  ],
})
export class DataTable {
  /** Colonnes affichées (ordre = ordre des `<th>` / cellules). */
  readonly columns = input<string[]>([]);
  /** Lignes de données (un objet par ligne ; les valeurs lues via `columns`). */
  readonly rows = input<Record<string, unknown>[]>([]);

  /** Rendu typé d'une cellule (= DataCell.kt) : bool → marqueur coloré, date → court, uuid → mono. */
  protected cell(col: string, v: unknown): { text: string; cls: string } {
    if (v === null || v === undefined) return { text: '—', cls: 'dt--null' };
    if (typeof v === 'boolean') return { text: v ? '✓' : '–', cls: v ? 'dt--true' : 'dt--false' };
    if (typeof v === 'number') return { text: String(v), cls: 'dt--num' };
    if (typeof v === 'string') {
      if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(v)) return { text: v.slice(0, 10) + ' ' + v.slice(11, 16), cls: 'dt--date' };
      if (/uuid/i.test(col)) return { text: v, cls: 'dt--mono' };
    }
    return { text: String(v), cls: '' };
  }
}
