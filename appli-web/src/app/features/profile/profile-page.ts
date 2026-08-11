import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { fromEvent, map, merge, of } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { getClientId } from '@core/auth/client-id';
import { MeProfileUpdate } from '@core/models/user.model';
import { SnackbarService } from '@core/snackbar/snackbar.service';
import { SyncEngine } from '@core/sync/sync-engine';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { DetailRow } from '@designsystem/common_components/detail-row';
import { ActionIconWithTextButton } from '@designsystem/common_components/action-icon-with-text-button';
import { FormDialog } from '@designsystem/common_components/form-dialog';
import { CustomTextField } from '@designsystem/common_components/custom-text-field';
import { CustomRadioButton } from '@designsystem/common_components/custom-radio-button';
import { CustomDatePickerDialog } from '@designsystem/common_components/custom-date-picker-dialog';
import { DateField } from '@designsystem/common_components/date-field';

const SEX_OPTIONS: { value: 'MALE' | 'FEMALE' | 'OTHER'; label: string }[] = [
  { value: 'MALE', label: 'Homme' },
  { value: 'FEMALE', label: 'Femme' },
  { value: 'OTHER', label: 'Autre' },
];

/**
 * Profil — refonte fidèle de ProfileScreen.kt (flat) : carte identité (avatar +
 * nom + @username + badge rôle), sections TitledDivider Compte / Bio / Infos app
 * (cards bgRecessed de DetailRows), actions Modifier (vert) + Rafraîchir, zone
 * sensible Supprimer le compte (rouge). Dialogs : édition (PATCH /me/profile,
 * champs vides omis = inchangés) + suppression (DELETE /me, mot de passe requis,
 * purge Dexie + logout). Déviations web : « Token valide » = session authentifiée
 * (TokenStore), « Réseau » = navigator.onLine, « Non synchronisées » = compteurs
 * SyncEngine ; bouton confirm du dialog suppression en couleur primaire (FormDialog
 * n'expose pas la couleur).
 */
@Component({
  selector: 'app-profile-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ScreenTitleBar,
    TitledDivider,
    DetailRow,
    ActionIconWithTextButton,
    FormDialog,
    CustomTextField,
    CustomRadioButton,
    CustomDatePickerDialog,
    DateField,
  ],
  template: `
    <section class="page">
      <app-screen-title-bar title="Profil" />

      <div class="page__body">
        <!-- 2 colonnes (comme les autres pages) : identité/compte/bio à gauche, le reste à droite. -->
        <div class="split">
        <!-- GAUCHE : identité + compte + bio -->
        <div class="split__col">
        <!-- 🪪 Carte identité : avatar + nom + @username + badge rôle -->
        <div class="idcard">
          <span class="idcard__avatar ms">account_circle</span>
          <div class="idcard__info">
            <span class="idcard__name">{{ displayName() }}</span>
            <span class="idcard__username">{{ '@' + (user()?.username ?? dash) }}</span>
            <span class="idcard__role" [class.idcard__role--admin]="user()?.isAdmin">
              {{ user()?.isAdmin ? 'Admin' : 'Utilisateur' }}
            </span>
          </div>
        </div>

        <!-- 📇 Compte -->
        <app-titled-divider title="Compte" />
        <div class="card">
          <app-detail-row icon="mail" [iconColor]="pa" label="E-mail" [labelColor]="pa" [value]="user()?.email ?? dash" />
          <app-detail-row icon="account_circle" [iconColor]="pa" label="Prénom" [labelColor]="pa" [value]="orDash(user()?.firstName)" />
          <app-detail-row icon="account_circle" [iconColor]="pa" label="Nom" [labelColor]="pa" [value]="orDash(user()?.lastName)" />
        </div>

        <!-- 🧬 Bio -->
        <app-titled-divider title="Bio" />
        <div class="card">
          <app-detail-row icon="calendar_today" [iconColor]="pa" label="Date de naissance" [labelColor]="pa" [value]="orDash(user()?.birthDate)" />
          <app-detail-row icon="info" [iconColor]="pa" label="Sexe" [labelColor]="pa" [value]="sexLabel()" />
          <app-detail-row icon="info" [iconColor]="pa" label="Taille" [labelColor]="pa" [value]="heightLabel()" />
          <app-detail-row icon="info" [iconColor]="pa" label="Poids" [labelColor]="pa" [value]="weightLabel()" />
        </div>

        </div>
        <!-- DROITE : infos app + actions + zone sensible -->
        <div class="split__col">
        <!-- ⚙️ Infos app -->
        <app-titled-divider title="Infos app" />
        <div class="card">
          <app-detail-row icon="info" [iconColor]="pa" label="ID utilisateur" [labelColor]="pa" [value]="user()?.id?.toString() ?? dash" />
          <app-detail-row icon="info" [iconColor]="pa" label="ID client" [labelColor]="pa" [value]="clientId" />
          <app-detail-row
            icon="check_circle"
            [iconColor]="tokenValid() ? green : orange"
            label="Token valide"
            [labelColor]="pa"
            [value]="yn(tokenValid())"
            [valueColor]="tokenValid() ? green : orange"
          />
          <app-detail-row
            [icon]="online() ? 'cloud_done' : 'cloud_off'"
            [iconColor]="online() ? green : orange"
            label="Réseau"
            [labelColor]="pa"
            [value]="yn(online())"
            [valueColor]="online() ? green : orange"
          />
          <app-detail-row
            [icon]="hasUnsynced() ? 'cloud_off' : 'cloud_done'"
            [iconColor]="hasUnsynced() ? yellow : green"
            label="Données non synchronisées"
            [labelColor]="pa"
            [value]="yn(hasUnsynced())"
            [valueColor]="hasUnsynced() ? yellow : green"
          />
        </div>

        @if (error(); as e) {
          <p class="err">⚠️ {{ e }}</p>
        }

        <!-- 🔘 Actions : Modifier + Rafraîchir -->
        <div class="actions">
          <app-action-icon-with-text-button
            icon="edit"
            text="Modifier le profil"
            [backgroundColor]="green"
            [fullWidth]="true"
            [disabled]="!user()"
            (clicked)="openEdit()"
          />
          <app-action-icon-with-text-button
            icon="refresh"
            text="Rafraîchir"
            [fullWidth]="true"
            (clicked)="refresh()"
          />
        </div>

        <!-- ⚠️ Zone sensible -->
        <app-titled-divider title="Zone sensible" />
        <app-action-icon-with-text-button
          icon="delete_forever"
          text="Supprimer le compte"
          backgroundColor="var(--app-btn-danger-bg)"
          tint="var(--app-btn-danger-fg)"
          textColor="var(--app-btn-danger-fg)"
          [fullWidth]="true"
          (clicked)="openDelete()"
        />
        </div>
        </div>
      </div>
    </section>

    <!-- ✏️ Dialog d'édition du profil (PATCH /me/profile) -->
    <app-form-dialog
      [open]="editOpen()"
      title="Modifier le profil"
      confirmText="Enregistrer"
      [scrollable]="true"
      (confirm)="saveEdit()"
      (dismiss)="editOpen.set(false)"
    >
      <app-custom-text-field label="E-mail" placeholder="E-mail" type="email" [value]="editEmail()" (valueChange)="editEmail.set($event)" />
      <app-custom-text-field label="Prénom" placeholder="Prénom" [value]="editFirstName()" (valueChange)="editFirstName.set($event)" />
      <app-custom-text-field label="Nom" placeholder="Nom" [value]="editLastName()" (valueChange)="editLastName.set($event)" />

      <span class="fld__label">Date de naissance</span>
      <app-date-field [value]="editBirthDate()" (clicked)="datePickerOpen.set(true)" />

      <span class="fld__label">Sexe</span>
      <div class="fld__radios" role="radiogroup">
        @for (opt of sexOptions; track opt.value) {
          <button type="button" class="fld__radiorow" (click)="editSex.set(opt.value)">
            <app-custom-radio-button [selected]="editSex() === opt.value" />
            <span class="fld__radiolabel">{{ opt.label }}</span>
          </button>
        }
      </div>

      <app-custom-text-field label="Taille" placeholder="ex. 175 cm" [value]="editHeight()" (valueChange)="editHeight.set($event)" />
      <app-custom-text-field label="Poids" placeholder="ex. 72 kg" [value]="editWeight()" (valueChange)="editWeight.set($event)" />
    </app-form-dialog>

    <app-custom-date-picker-dialog
      [open]="datePickerOpen()"
      title="Date de naissance"
      [initialIso]="editBirthDate() || defaultBirthIso"
      (confirm)="editBirthDate.set($event); datePickerOpen.set(false)"
      (dismiss)="datePickerOpen.set(false)"
    />

    <!-- 🗑️ Dialog de suppression de compte (DELETE /me) -->
    <app-form-dialog
      [open]="deleteOpen()"
      title="Supprimer votre compte ?"
      confirmText="Supprimer"
      [confirmEnabled]="deletePassword().length > 0 && !deleting()"
      (confirm)="confirmDelete()"
      (dismiss)="closeDelete()"
    >
      <p class="del__body">
        Cette action supprime définitivement votre compte et toutes vos données — séances,
        exercices, statistiques, routines. Elle est irréversible.
      </p>
      <app-custom-text-field
        label="Confirmez votre mot de passe"
        placeholder="Mot de passe"
        type="password"
        [value]="deletePassword()"
        (valueChange)="deletePassword.set($event); deleteError.set(null)"
      />
      @if (deleteError(); as e) {
        <p class="err">{{ e }}</p>
      }
    </app-form-dialog>
  `,
  styles: [
    `
      /* Title bar pleine largeur (hors corps) ; corps capé lisible + centré. */
      .page__body {
        padding: var(--page-gutter-top) var(--page-gutter) var(--space-5);
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      /* 2 colonnes (comme Objectifs / Sync) : identité+compte+bio | infos app+actions+zone sensible. */
      .split {
        display: flex;
        gap: var(--page-gutter);
        align-items: flex-start;
      }
      .split__col {
        flex: 1 1 0;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      @media (max-width: 900px) {
        .split {
          flex-direction: column;
        }
      }
      /* 🪪 Carte identité — Row(bgRecessed, radius 16, padding 20, gap 16). */
      .idcard {
        display: flex;
        align-items: center;
        gap: var(--space-4);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-lg);
        padding: var(--space-5);
      }
      .idcard__avatar {
        font-family: 'Material Symbols Outlined';
        font-size: 64px;
        line-height: 1;
        color: var(--app-primary-action);
        user-select: none;
      }
      .idcard__info {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: var(--space-1);
      }
      .idcard__name {
        font-size: 20px;
        font-weight: 600;
        color: var(--app-text-primary);
      }
      .idcard__username {
        font-size: 14px;
        color: var(--app-text-tertiary);
      }
      /* Badge rôle — pill alpha 0.18 (Admin blueMedium / Utilisateur lightGrayBlue). */
      .idcard__role {
        border-radius: var(--radius-md);
        padding: var(--space-1) var(--space-3);
        font-size: 12px;
        font-weight: var(--font-weight-medium);
        color: var(--c-light-gray-blue);
        background: color-mix(in srgb, var(--c-light-gray-blue) 18%, transparent);
      }
      .idcard__role--admin {
        color: var(--c-blue-medium);
        background: color-mix(in srgb, var(--c-blue-medium) 18%, transparent);
      }
      /* ≈ ProfileSectionCard : bloc bgRecessed arrondi, DetailRows espacées 8px. */
      .card {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: var(--space-2);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-lg);
        padding: var(--space-4);
      }
      .err {
        margin: 0;
        font-size: var(--font-size-caption);
        color: var(--c-red-medium);
      }
      .actions {
        display: flex;
        gap: var(--space-3);
        margin-top: var(--space-1);
      }
      /* Champs custom du dialog d'édition (date + radios sexe), style CustomTextField. */
      .fld__label {
        font-size: var(--font-size-caption);
        color: var(--app-text-tertiary);
      }
      .fld__radios {
        display: flex;
        flex-direction: column;
        margin-top: calc(-1 * var(--space-2));
      }
      .fld__radiorow {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        height: 40px;
        width: 100%;
        background: transparent;
        border: none;
        padding: 0;
        cursor: pointer;
        text-align: left;
        appearance: none;
        -webkit-appearance: none;
      }
      .fld__radiolabel {
        font-family: var(--font-family-base);
        font-size: var(--font-size-body);
        color: var(--app-text-primary);
      }
      .del__body {
        margin: 0;
        font-size: var(--font-size-body);
        color: var(--app-text-primary);
      }
    `,
  ],
})
export class ProfilePage {
  private readonly auth = inject(AuthService);
  private readonly syncEngine = inject(SyncEngine);
  private readonly snackbar = inject(SnackbarService);
  private readonly router = inject(Router);

  protected readonly user = this.auth.currentUser;
  protected readonly error = signal<string | null>(null);

  // ----- Constantes template (couleurs tokens + helpers) -----
  protected readonly dash = '—';
  protected readonly pa = 'var(--app-primary-action)';
  protected readonly green = 'var(--c-medium-green)';
  protected readonly orange = 'var(--c-orange-medium)';
  protected readonly yellow = 'var(--c-yellow-medium)';
  protected readonly clientId = getClientId();
  protected readonly sexOptions = SEX_OPTIONS;
  /** Date par défaut du picker naissance (≈ LocalDate.now().minusYears(25) Android). */
  protected readonly defaultBirthIso = `${new Date().getFullYear() - 25}-01-01`;

  // ----- Infos app : token / réseau / unsynced (déviations web documentées) -----
  protected readonly tokenValid = this.auth.isAuthenticated;
  protected readonly online = toSignal(
    merge(
      of(navigator.onLine),
      fromEvent(window, 'online').pipe(map(() => true)),
      fromEvent(window, 'offline').pipe(map(() => false)),
    ),
    { initialValue: navigator.onLine },
  );
  private readonly stats = toSignal(this.syncEngine.allStats(), { initialValue: [] });
  protected readonly hasUnsynced = computed(() =>
    this.stats().some((s) => s.unsynced > 0 || s.pendingDeletion > 0),
  );

  // ----- Affichage -----
  protected readonly displayName = computed(() => {
    const u = this.user();
    const full = [u?.firstName, u?.lastName].filter((s) => s && s.trim()).join(' ');
    return full || u?.username || this.dash;
  });
  protected readonly sexLabel = computed(
    () => SEX_OPTIONS.find((o) => o.value === this.user()?.sex)?.label ?? this.dash,
  );
  protected readonly heightLabel = computed(() => {
    const h = this.user()?.heightCm;
    return h != null ? `${trimNum(h)} cm` : this.dash;
  });
  protected readonly weightLabel = computed(() => {
    const w = this.user()?.weightKg;
    return w != null ? `${trimNum(w)} kg` : this.dash;
  });

  protected orDash(v: string | null | undefined): string {
    return v && v.trim() ? v : this.dash;
  }
  protected yn(b: boolean): string {
    return b ? 'Oui' : 'Non';
  }

  // ----- Dialog d'édition -----
  protected readonly editOpen = signal(false);
  protected readonly datePickerOpen = signal(false);
  protected readonly editEmail = signal('');
  protected readonly editFirstName = signal('');
  protected readonly editLastName = signal('');
  protected readonly editBirthDate = signal('');
  protected readonly editSex = signal<'MALE' | 'FEMALE' | 'OTHER' | ''>('');
  protected readonly editHeight = signal('');
  protected readonly editWeight = signal('');

  // ----- Dialog de suppression -----
  protected readonly deleteOpen = signal(false);
  protected readonly deletePassword = signal('');
  protected readonly deleteError = signal<string | null>(null);
  protected readonly deleting = signal(false);

  constructor() {
    if (this.auth.isAuthenticated() && !this.auth.currentUser()) {
      this.auth.loadMe().subscribe({
        error: (e) => this.error.set(e?.message ?? 'Échec du chargement du profil'),
      });
    }
  }

  protected refresh(): void {
    this.auth.loadMe().subscribe({
      next: () => {
        this.error.set(null);
        this.snackbar.success('Profil rafraîchi', { duration: 'SHORT' });
      },
      error: (e) => this.error.set(e?.message ?? 'Échec du chargement du profil'),
    });
  }

  protected openEdit(): void {
    const u = this.user();
    if (!u) return;
    this.editEmail.set(u.email ?? '');
    this.editFirstName.set(u.firstName ?? '');
    this.editLastName.set(u.lastName ?? '');
    this.editBirthDate.set(u.birthDate ?? '');
    this.editSex.set(u.sex ?? '');
    this.editHeight.set(u.heightCm != null ? trimNum(u.heightCm) : '');
    this.editWeight.set(u.weightKg != null ? trimNum(u.weightKg) : '');
    this.editOpen.set(true);
  }

  /** Champs vidés = omis du PATCH (= inchangés côté serveur, miroir Gson Android). */
  protected saveEdit(): void {
    const body: MeProfileUpdate = {};
    const put = (k: keyof MeProfileUpdate, v: string) => {
      if (v.trim()) (body[k] as unknown) = v.trim();
    };
    put('email', this.editEmail());
    put('firstName', this.editFirstName());
    put('lastName', this.editLastName());
    put('birthDate', this.editBirthDate());
    put('sex', this.editSex());
    const h = parseFloat(this.editHeight().replace(',', '.'));
    if (!isNaN(h)) body.heightCm = h;
    const w = parseFloat(this.editWeight().replace(',', '.'));
    if (!isNaN(w)) body.weightKg = w;

    this.auth.updateMeProfile(body).subscribe({
      next: () => {
        this.editOpen.set(false);
        this.snackbar.success('Profil mis à jour', { duration: 'SHORT' });
      },
      error: () => {
        this.editOpen.set(false);
        this.snackbar.error('Échec de la mise à jour du profil', { duration: 'SHORT' });
      },
    });
  }

  protected openDelete(): void {
    this.deletePassword.set('');
    this.deleteError.set(null);
    this.deleting.set(false);
    this.deleteOpen.set(true);
  }

  protected closeDelete(): void {
    if (!this.deleting()) this.deleteOpen.set(false);
  }

  /** DELETE /me puis purge Dexie + logout + redirection login (miroir deleteAccount Android). */
  protected confirmDelete(): void {
    this.deleting.set(true);
    this.auth.deleteMe(this.deletePassword()).subscribe({
      next: () => {
        void this.syncEngine.clearAll().finally(() => {
          this.snackbar.success('Votre compte a été supprimé', { duration: 'SHORT' });
          this.auth.logout();
          void this.router.navigate(['/login']);
        });
      },
      error: (e) => {
        this.deleting.set(false);
        this.deleteError.set(
          e?.status === 403
            ? 'Mot de passe incorrect.'
            : e?.status === 400
              ? 'Vous êtes le dernier administrateur. Promouvez un autre utilisateur administrateur avant de supprimer votre compte.'
              : 'Impossible de supprimer votre compte. Veuillez réessayer.',
        );
      },
    });
  }
}

/** "175.0" -> "175" ; "72.5" -> "72.5" (miroir trimNum Android). */
function trimNum(f: number): string {
  return f % 1 === 0 ? String(Math.trunc(f)) : String(f);
}
