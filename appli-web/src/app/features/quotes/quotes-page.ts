import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { liveQuery } from 'dexie';
import { from } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { LocalQuote } from '@core/models/quote.model';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';
import { uuidv4 } from '@core/utils/uuid';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { StyledSearchField } from '@designsystem/common_components/styled-search-field';
import { EmptyListRow } from '@designsystem/common_components/empty-list-row';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { FormDialog } from '@designsystem/common_components/form-dialog';
import { ConfirmationDialog } from '@designsystem/common_components/confirmation-dialog';
import { CustomTextField } from '@designsystem/common_components/custom-text-field';
import { OptionsBottomSheet, type SheetAction } from '@designsystem/common_components/options-bottom-sheet';

/**
 * Écran Citations — miroir de QuotesScreen.kt assemblé depuis le design system :
 * ScreenTitleBar + section Actions (StyledSearchField filtre + bouton ajouter) + TitledDivider
 * compteur + liste de QuoteRow (texte + auteur + ⋮ → OptionsBottomSheet édition/suppression) +
 * FormDialog (ajout/édition) + ConfirmationDialog (suppression). Filtre texte OU auteur.
 * Données offline-first (Dexie liveQuery → signal). CRUD local optimiste + sync best-effort.
 */
@Component({
  selector: 'app-quotes-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ScreenTitleBar,
    TitledDivider,
    StyledSearchField,
    EmptyListRow,
    ActionIconButton,
    FormDialog,
    ConfirmationDialog,
    CustomTextField,
    OptionsBottomSheet,
  ],
  template: `
    <section class="page">
      <app-screen-title-bar title="Citations motivantes" />

      <div class="page__body">
        <app-titled-divider title="Actions" />
        <div class="actions">
          <app-styled-search-field
            class="actions__search"
            [value]="search()"
            (valueChange)="search.set($event)"
            placeholderText="Rechercher une citation"
          />
          <app-action-icon-button icon="add" (clicked)="openAdd()" />
        </div>

        <app-titled-divider [title]="countText()" />

        @if (filtered().length === 0) {
          <app-empty-list-row [text]="emptyText()" icon="book" />
        } @else {
          <div class="list">
            @for (q of filtered(); track q.uuid) {
              <div class="qrow">
                <div class="qrow__main">
                  <span class="qrow__text">"{{ q.text }}"</span>
                  @if (q.author) {
                    <span class="qrow__author">— {{ q.author }}</span>
                  }
                </div>
                <app-action-icon-button icon="more_vert" (clicked)="forOptions.set(q)" />
              </div>
            }
          </div>
        }
      </div>

      <app-options-bottom-sheet
        [open]="forOptions() !== null"
        title="Citation"
        [actions]="quoteActions"
        (dismissRequest)="forOptions.set(null)"
        (actionSelected)="onOption($event)"
      />

      <app-form-dialog
        [open]="showForm()"
        [title]="formTitle()"
        [confirmText]="formConfirm()"
        [confirmEnabled]="formText().trim().length > 0"
        disabledReason="La citation ne peut pas être vide"
        (confirm)="submitForm()"
        (dismiss)="showForm.set(false)"
      >
        <app-custom-text-field
          label="Citation"
          placeholder="ex. Le corps réalise ce que l'esprit croit."
          [multiline]="true"
          [value]="formText()"
          (valueChange)="formText.set($event)"
        />
        <app-custom-text-field
          label="Auteur (facultatif)"
          placeholder="ex. Inconnu"
          [value]="formAuthor()"
          (valueChange)="formAuthor.set($event)"
        />
      </app-form-dialog>

      <app-confirmation-dialog
        [open]="toDelete() !== null"
        title="Supprimer la citation"
        message="Voulez-vous supprimer cette citation ?"
        confirmButtonText="Supprimer"
        dismissButtonText="Annuler"
        (confirm)="confirmDelete()"
        (dismiss)="toDelete.set(null)"
      />
    </section>
  `,
  styles: [
    `
      /* Title bar pleine largeur (hors corps) ; le corps prend la gouttière (--page-gutter). */
      .page__body {
        padding: var(--page-gutter-top) var(--page-gutter) var(--space-4);
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      .actions {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
      .actions__search {
        flex: 1;
      }
      /* Grille de "cartes citation" responsive : remplit la largeur (mur de citations). */
      .list {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
        gap: var(--space-2);
        align-items: start;
      }
      .qrow {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: 10px var(--space-3) 10px 14px;
      }
      .qrow__main {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
      }
      .qrow__text {
        color: var(--app-text-primary);
        font-size: 15px;
      }
      .qrow__author {
        color: var(--app-primary-action);
        font-size: 13px;
        font-weight: var(--font-weight-medium);
        margin-top: 2px;
      }
    `,
  ],
})
export class QuotesPage {
  private readonly db = inject(AppDb);
  private readonly sync = inject(SyncEngine);
  private readonly auth = inject(AuthService);

  protected readonly quotes = toSignal(
    from(liveQuery(() => this.db.quotes.filter((q) => !q.pendingDeletion).toArray())),
    { initialValue: [] as LocalQuote[] },
  );
  protected readonly search = signal('');

  protected readonly forOptions = signal<LocalQuote | null>(null);
  protected readonly toDelete = signal<LocalQuote | null>(null);
  protected readonly showForm = signal(false);
  protected readonly editing = signal<LocalQuote | null>(null);
  protected readonly formText = signal('');
  protected readonly formAuthor = signal('');

  protected readonly quoteActions: SheetAction[] = [
    { label: 'Modifier', icon: 'edit', color: 'var(--c-blue-medium)' },
    { label: 'Supprimer', icon: 'delete', color: 'var(--c-red-medium)' },
  ];

  protected readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    if (!q) return this.quotes();
    return this.quotes().filter(
      (item) => item.text.toLowerCase().includes(q) || (item.author ?? '').toLowerCase().includes(q),
    );
  });
  // Pluriel calqué sur R.plurals.quotes_count ("%d citation" / "%d citations").
  protected readonly countText = computed(() => {
    const n = this.filtered().length;
    return `${n} citation${n > 1 ? 's' : ''}`;
  });
  protected readonly emptyText = computed(() =>
    this.search().trim() ? 'Aucune citation.' : 'Aucune citation. Clique sur + pour en ajouter une.',
  );
  protected readonly formTitle = computed(() => (this.editing() ? 'Modifier la citation' : 'Nouvelle citation'));
  protected readonly formConfirm = computed(() => (this.editing() ? 'Enregistrer' : 'Ajouter'));

  constructor() {
    void this.sync.syncAll().catch(() => undefined);
  }

  protected openAdd(): void {
    this.editing.set(null);
    this.formText.set('');
    this.formAuthor.set('');
    this.showForm.set(true);
  }

  protected openEdit(q: LocalQuote): void {
    this.editing.set(q);
    this.formText.set(q.text);
    this.formAuthor.set(q.author ?? '');
    this.showForm.set(true);
  }

  protected onOption(label: string): void {
    const q = this.forOptions();
    this.forOptions.set(null);
    if (!q) return;
    if (label === 'Modifier') this.openEdit(q);
    else if (label === 'Supprimer') this.toDelete.set(q);
  }

  protected async submitForm(): Promise<void> {
    const text = this.formText().trim();
    if (!text) return;
    const author = this.formAuthor().trim() || null;
    const now = new Date().toISOString();
    const editing = this.editing();
    if (editing) {
      await this.db.quotes.update(editing.uuid, { text, author, synced: false, updatedAt: now });
    } else {
      const row: LocalQuote = {
        uuid: uuidv4(),
        userId: this.auth.currentUser()?.id ?? 0,
        text,
        author,
        updatedAt: now,
        synced: false,
        pendingDeletion: false,
      };
      await this.db.quotes.put(row);
    }
    this.showForm.set(false);
    void this.sync.syncAll().catch(() => undefined);
  }

  protected confirmDelete(): void {
    const q = this.toDelete();
    this.toDelete.set(null);
    if (!q) return;
    void this.db.quotes
      .update(q.uuid, { pendingDeletion: true, updatedAt: new Date().toISOString() })
      .then(() => this.sync.syncAll().catch(() => undefined));
  }
}
