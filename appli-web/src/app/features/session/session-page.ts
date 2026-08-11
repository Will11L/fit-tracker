import { ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { liveQuery } from 'dexie';
import { from } from 'rxjs';
import { LocalActualWorkout } from '@core/models/actual-workout.model';
import { LocalActualWorkoutExercise } from '@core/models/actual-workout-exercise.model';
import { LocalActualWorkoutSet } from '@core/models/actual-workout-set.model';
import { LocalExercise } from '@core/models/exercise.model';
import { LocalMuscle } from '@core/models/muscle.model';
import { LocalExerciseMuscle } from '@core/models/exercise-muscle.model';
import { LocalEquipment } from '@core/models/equipment.model';
import { LocalExerciseEquipment } from '@core/models/exercise-equipment.model';
import { AppDb } from '@core/sync/dexie-db';
import { SyncEngine } from '@core/sync/sync-engine';
import { SnackbarService } from '@core/snackbar/snackbar.service';
import { uuidv4 } from '@core/utils/uuid';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { FramedSection } from '@designsystem/common_components/framed-section';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { LabeledProgressBar } from '@designsystem/common_components/labeled-progress-bar';
import { SummaryRow, type SummaryItemData } from '@designsystem/common_components/summary-row';
import { SummaryItem } from '@designsystem/common_components/summary-item';
import { EmptyListRow } from '@designsystem/common_components/empty-list-row';
import { FormDialog } from '@designsystem/common_components/form-dialog';
import { CustomTextField } from '@designsystem/common_components/custom-text-field';
import { HorizontalNumberPicker } from '@designsystem/common_components/horizontal-number-picker';
import { SetRow, type SetRowData } from '@designsystem/common_components/set-row';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { AppIcon } from '@designsystem/icons/app-icon';
import { ExercisePickerBottomSheet, type ExercisePickerItem } from '@designsystem/common_components/exercise-picker-bottom-sheet';
import { PhasePickerDialog } from '@designsystem/common_components/phase-picker-dialog';
import { OptionsBottomSheet, type SheetAction } from '@designsystem/common_components/options-bottom-sheet';
import { StatusPickerDialog, type StatusOption } from '@designsystem/common_components/status-picker-dialog';
import { RevealIn } from '@designsystem/common_components/reveal-in';

interface ExoView {
  uuid: string;
  exerciseUUID: string;
  name: string;
  phase: string;
  order: number;
  repsMin: number;
  repsMax: number;
  repsLabel: string;
  sets: LocalActualWorkoutSet[];
  doneCount: number; // séries réelles (hors dropsets) terminées
  realSetCount: number; // séries réelles (hors dropsets) — base du comptage « X/Y séries »
  status: string;
  synced: boolean;
  lastWeight: number | null; // poids max de la dernière séance où l'exo a été fait (échelle/repère)
  repsShort: string; // reps compactes pour la colonne étroite (ex. « 8-12 », sans espaces)
  volume: number; // tonnage Σ poids×reps des séries faites de l'exo (dropsets inclus)
}

/**
 * Écran Séance (v1 fonctionnelle) — miroir condensé de SessionTab/SessionExerciseScreen.kt :
 * une séance réalisée (`actual_workout`) avec ses exercices + séries éditables. Interactions
 * (écritures optimistes Dexie → syncAll) : cycler le statut d'une série (NOT_STARTED → IN_PROGRESS
 * → DONE), éditer reps/poids/note, supprimer/ajouter une série, marquer la séance terminée.
 * `uuid` lié depuis la route (withComponentInputBinding). Visuel volontairement minimal (à raffiner).
 * Déféré : ajout/réordo d'exercices, supersets, pickers phase/statut, dropsets, écran détail exercice.
 */
@Component({
  selector: 'app-session-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ScreenTitleBar,
    FramedSection,
    TitledDivider,
    LabeledProgressBar,
    SummaryRow,
    SummaryItem,
    EmptyListRow,
    ActionIconButton,
    AppIcon,
    OptionsBottomSheet,
    StatusPickerDialog,
    ExercisePickerBottomSheet,
    PhasePickerDialog,
    FormDialog,
    CustomTextField,
    HorizontalNumberPicker,
    SetRow,
    RevealIn,
  ],
  template: `
    <section class="page">
      @if (!embedded()) { <app-screen-title-bar [title]="view().workout?.name || 'Séance'" /> }

      <div class="page__body">

        @if (view().workout; as w) {
          <div class="split" [class.split--stacked]="embedded()">
            <!-- Master (gauche en autonome, au-dessus en embarqué) :
                 lignes compactes par phase (nom · sync · faites/total · chevron). -->
            <div class="split__list">
              <!-- Complétion de la séance dans un cadre thirdBlue (en-tête = titled-divider) ; la barre
                   et les 2 tuiles Séries/Exercices passent en fond secondBlue pour ressortir. -->
              <app-framed-section title="Complétion de la séance">
                <div class="completion">
                  <app-summary-row class="completion__summary" [items]="summaryItems()" [compact]="true" />
                  <div class="completion__progress">
                  <app-labeled-progress-bar
                    class="completion__bar"
                    [progress]="stats().progress"
                    troughColor="var(--c-second-blue)"
                  />
                  <div class="completion__actions">
                    <app-action-icon-button
                      [icon]="w.synced ? 'cloud_done' : 'cloud_off'"
                      [hasBackground]="false"
                      [iconSize]="26"
                      [tint]="w.synced ? 'var(--app-primary-action)' : 'var(--c-yellow-medium)'"
                      (clicked)="onSync()"
                    />
                    <!-- Icône 26 = même taille que la flèche d'avancement de la colonne droite. -->
                    <app-action-icon-button
                      [icon]="w.isDone ? 'check_circle' : 'arrow_circle_up'"
                      [hasBackground]="false"
                      [iconSize]="26"
                      [tint]="w.isDone ? 'var(--c-medium-green)' : 'var(--c-blue-medium)'"
                      (clicked)="toggleDone(w)"
                    />
                    <!-- « + » d'ajout : fond primaire (demande user 2026-07-15). -->
                    <app-action-icon-button
                      icon="add"
                      [backgroundColor]="'var(--app-primary-action)'"
                      (clicked)="showAddSheet.set(true)"
                    />
                  </div>
                  </div>
                </div>
                </app-framed-section>

              <!-- 2ᵉ cadre : tout ce qui est sous la barre — en-tête (légende) + rows d'exercices par phase. -->
              <app-framed-section>
              <!-- Légende : même grille que les rows (colonnes de largeur uniforme, gap uniforme,
                   seul le nom prend le reste). -->
              <div class="exrow exleg">
                <span class="exrow__col exrow__col--order">N°</span>
                <span class="exleg__name">Exercice</span>
                <span class="exrow__col exrow__col--reps">Reps</span>
                <span class="exrow__col exrow__col--kg">Kg</span>
                <span class="exrow__col">Sync</span>
                <span class="exrow__col">Sets</span>
                <span class="exrow__col">Voir</span>
              </div>
              <!-- Phases (dans le 2ᵉ cadre) : sous-titres (titled-divider) + rows à colonnes uniformes. -->
              @for (phase of phases; track phase.key) {
                <app-titled-divider class="detail-sub" [title]="phase.label" color="var(--c-gray-blue)" />
                @if (byPhase()[phase.key].length === 0) {
                  <app-empty-list-row [text]="phase.empty" icon="fitness_center" [verticalPadding]="0" />
                } @else {
                  @for (ex of byPhase()[phase.key]; track ex.uuid) {
                    <div class="exrow phaserow">
                      <span class="exrow__col exrow__col--order"><button type="button" class="exrow__order-chip" (click)="openOrderOptions(ex)">{{ ex.order }}</button></span>
                      <button type="button" class="exrow__name" (click)="openExerciseOptions(ex)">{{ ex.name }}</button>
                      <span class="exrow__col exrow__col--reps">{{ ex.repsShort }}</span>
                      <span class="exrow__col exrow__col--kg">{{ ex.lastWeight !== null ? ex.lastWeight : '—' }}</span>
                      <span class="exrow__col">
                        <app-icon
                          [name]="ex.synced ? 'cloud_done' : 'cloud_off'"
                          [size]="20"
                          [color]="ex.synced ? 'var(--app-primary-action)' : 'var(--c-yellow-medium)'"
                        />
                      </span>
                      <span class="exrow__col">{{ ex.doneCount }} / {{ ex.realSetCount }}</span>
                      <span class="exrow__col">
                        <!-- Statut : ActionIconButton dense (30/18). -->
                        <app-action-icon-button
                          [icon]="statusIcon(ex.status)"
                          [size]="30"
                          [iconSize]="18"
                          [backgroundColor]="statusBg(ex.status)"
                          tint="var(--app-text-primary)"
                          (clicked)="selectedExo.set(ex.uuid)"
                        />
                      </span>
                    </div>
                  }
                }
              }
              </app-framed-section>
            </div>

            <!-- Detail (droite en autonome, en dessous en embarqué) : reprise du design de l'écran
                 « exercice en cours » Android (SessionExerciseScreen) — Détails (N°/Sets/Reps +
                 actions) · Progression (barre + ajout de série) · tableau des séries éditables ·
                 Notes (description de l'exercice, éditable) · Instructions (lecture seule).
                 Entre en slide-down + fade ; re-animée (fondu seul) au changement d'exercice. -->
            <div class="split__detail" [appRevealIn]="selectedExo()">
              @if (selectedExoView(); as ex) {
                <!-- 1er cadre (thirdBlue) : nom de l'exo en en-tête (titled-divider), tuiles
                     N°/Séries/Reps + barre d'avancement (actions à droite). Tout ce qui est sous la
                     barre (séries, notes, instructions) est regroupé dans un 2ᵉ cadre. -->
                <app-framed-section [title]="ex.name">
                  <div class="exohead">
                    <!-- N° d'ordre : tuile label · value (sans icône). Puis tuiles Séries/Reps/Charge/
                         Volume (avec icône). Actions de l'exo sur la ligne de la barre. -->
                    <app-summary-item
                      class="exohead__tile"
                      [value]="ex.order.toString()"
                      label="N°"
                      [compact]="true"
                    />
                    @for (s of exoStats(ex); track s.label) {
                      <app-summary-item
                        class="exohead__tile"
                        [icon]="s.icon"
                        [value]="s.value"
                        [label]="s.label"
                        [iconTint]="s.iconTint"
                        [compact]="true"
                      />
                    }
                  </div>
                <!-- Barre d'avancement (actions état · sync · stats · « + » à droite) : dernier
                     élément du 1er cadre. -->
                <div class="exoprog">
                  <app-labeled-progress-bar
                    class="exoprog__bar"
                    [progress]="exProgress(ex)"
                    troughColor="var(--c-second-blue)"
                  />
                  <!-- Actions de l'exo à droite de la barre : état · sync · stats, puis « + » tout à droite. -->
                  <!-- Flèche d'avancement en bleu comme la colonne de gauche (demande user 2026-07-15). -->
                  <app-icon
                    [name]="exDone(ex) ? 'check_circle' : 'arrow_circle_up'"
                    [size]="26"
                    [color]="exDone(ex) ? 'var(--c-medium-green)' : 'var(--c-blue-medium)'"
                  />
                  <app-action-icon-button
                    [icon]="ex.synced ? 'cloud_done' : 'cloud_off'"
                    [hasBackground]="false"
                    [iconSize]="26"
                    [tint]="ex.synced ? 'var(--app-primary-action)' : 'var(--c-yellow-medium)'"
                    (clicked)="onSync()"
                  />
                  <!-- Stats de l'exo : fond secondBlue · « + » : fond primaire (demande user 2026-07-15). -->
                  <app-action-icon-button
                    icon="monitoring"
                    [backgroundColor]="'var(--c-second-blue)'"
                    (clicked)="goToExercise(ex)"
                  />
                  <app-action-icon-button
                    icon="add"
                    [backgroundColor]="'var(--app-primary-action)'"
                    (clicked)="addSet(ex)"
                  />
                </div>
                </app-framed-section>

                <!-- 2ᵉ cadre : tout ce qui est sous la barre d'avancement — tableau des séries, Notes,
                     Instructions (les 2 anciens cadres Notes/Instructions deviennent des sous-sections). -->
                <app-framed-section>
                <!-- Tableau des séries : en-tête 7 colonnes aligné avec app-set-row (SetTableHeader.kt). -->
                <div class="settable">
                  <div class="settable__head">
                    <span class="settable__h settable__h--idx">N°</span>
                    <span class="settable__h settable__h--reps">Rép.</span>
                    <span class="settable__h settable__h--weight">Poids</span>
                    <span class="settable__h settable__h--ico">Tend.</span>
                    <span class="settable__h settable__h--ico">Fait</span>
                    <span class="settable__h settable__h--btn">Suppr.</span>
                    <span class="settable__h settable__h--btn">Note</span>
                  </div>
                  @for (s of ex.sets; track s.uuid) {
                    <app-set-row
                      [set]="toRow(s, ex)"
                      [targetRepsMin]="ex.repsMin"
                      [targetRepsMax]="ex.repsMax"
                      (indexClick)="setForOptions.set(s)"
                      (editRepsClick)="openEdit(s)"
                      (editWeightClick)="openEdit(s)"
                      (deleteClick)="deleteSet(s)"
                      (addNoteClick)="openEdit(s)"
                    />
                  }
                </div>

                <app-titled-divider class="detail-sub" title="Notes" />
                <app-custom-text-field
                  class="notesfield"
                  [multiline]="true"
                  [rows]="3"
                  placeholder="Écris une note sur cet exercice…"
                  [value]="noteDraft()"
                  (valueChange)="noteDraft.set($event)"
                  (blurred)="saveNote()"
                />

                <app-titled-divider class="detail-sub" title="Instructions" />
                <div class="instructions">
                  @if (selectedExoInstructions().length === 0) {
                    <p class="instructions__empty">Aucune instruction.</p>
                  } @else {
                    @for (step of selectedExoInstructions(); track $index) {
                      <p class="instructions__step">
                        <span class="instructions__num">• Étape {{ $index + 1 }} :</span> {{ step }}
                      </p>
                    }
                  }
                </div>
                </app-framed-section>
              } @else {
                <app-empty-list-row text="Sélectionne un exercice à gauche." icon="touch_app" />
              }
            </div>
          </div>
        } @else {
          <app-empty-list-row text="Séance introuvable." icon="fitness_center" />
        }
      </div>

      <app-form-dialog
        [open]="editing() !== null"
        title="Éditer la série"
        confirmText="Enregistrer"
        [confirmEnabled]="true"
        (confirm)="saveEdit()"
        (dismiss)="editing.set(null)"
      >
        <!-- Reps : sélecteur horizontal (n'importe quel chiffre) ; hors de la plage recommandée de
             l'exo = case rouge, sélection en bleu (= HorizontalNumberPicker.kt). -->
        <app-horizontal-number-picker
          label="Reps"
          [min]="0"
          [max]="50"
          [selected]="editRepsNum()"
          [targetMin]="repsTargetMin()"
          [targetMax]="repsTargetMax()"
          (selectedChange)="editReps.set($event.toString())"
        />
        <!-- Poids : sélecteur horizontal entier (0..200 kg) + boutons d'appoint décimal (+0,25 / +0,5 /
             +0,75) ajoutés à la partie entière. Le label affiche le total résultant. -->
        <app-horizontal-number-picker
          [label]="'Poids (kg) : ' + editWeight()"
          [min]="0"
          [max]="200"
          [selected]="editWeightNum()"
          (selectedChange)="editWeight.set(($event + editWeightFrac()).toString())"
        />
        <div class="fracrow">
          @for (fr of weightFracs; track fr.value) {
            <button
              type="button"
              class="fracbtn"
              [class.fracbtn--active]="editWeightFrac() === fr.value"
              (click)="editWeight.set((editWeightNum() + fr.value).toString())"
            >
              {{ fr.label }}
            </button>
          }
        </div>
        <app-custom-text-field label="Note" placeholder="Optionnel" [value]="editNote()" (valueChange)="editNote.set($event)" />
      </app-form-dialog>

      <app-exercise-picker-bottom-sheet
        [open]="showAddSheet()"
        title="Ajouter un exercice"
        [exercises]="addableExercises()"
        [equipmentOptions]="equipmentFilterOptions()"
        [muscleOptions]="muscleFilterOptions()"
        muscleLabel="Muscle"
        (selectExercise)="onPickExercise($event)"
        (dismissRequest)="showAddSheet.set(false)"
      />
      <app-phase-picker-dialog
        [open]="pendingExerciseUuid() !== null"
        (phaseSelected)="onPickPhase($event)"
        (dismiss)="pendingExerciseUuid.set(null)"
      />
      <!-- Changer de section d'un exo existant (action du sheet ouvert par le N° de la colonne). -->
      <app-phase-picker-dialog
        [open]="changingPhaseExo() !== null"
        title="Changer de section"
        (phaseSelected)="onChangePhase($event)"
        (dismiss)="changingPhaseExo.set(null)"
      />
      <app-options-bottom-sheet
        [open]="exoForOptions() !== null"
        [title]="exoForOptions()?.name ?? ''"
        [actions]="exoOptions()"
        (actionSelected)="onExoOption($event)"
        (dismissRequest)="exoForOptions.set(null)"
      />
      <!-- Réordonnancement d'un exo (clic sur le N° de la colonne de gauche) : Monter/Descendre. -->
      <app-options-bottom-sheet
        [open]="orderForOptions() !== null"
        [title]="'Déplacer « ' + (orderForOptions()?.name ?? '') + ' »'"
        [actions]="orderOptions()"
        (actionSelected)="onOrderOption($event)"
        (dismissRequest)="orderForOptions.set(null)"
      />
      <!-- Options d'une série (clic sur le N°) — = SetOptionsBottomSheet.kt. -->
      <app-options-bottom-sheet
        [open]="setForOptions() !== null && !showStatusSheet()"
        title="Options de la série"
        [actions]="setOptions"
        (actionSelected)="onSetOption($event)"
        (dismissRequest)="setForOptions.set(null)"
      />
      <!-- « Changer le statut » — = ChangeSetStatusDialog.kt : StatusPickerDialog (icône teintée par
           la couleur du statut, sélection + confirmation), mêmes couples icône/couleur qu'Android. -->
      <app-status-picker-dialog
        [open]="showStatusSheet()"
        title="Statut de la série"
        [options]="setStatusOptions"
        [selected]="setForOptions()?.status ?? ''"
        (confirm)="onPickSetStatus($event)"
        (dismiss)="showStatusSheet.set(false); setForOptions.set(null)"
      />
    </section>
  `,
  styles: [
    `
      /* Title bar pleine largeur (hors corps) ; corps avec gouttière (--page-gutter). */
      .page__body {
        padding: var(--page-gutter-top) var(--page-gutter) var(--space-5);
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      /* Master-detail. Autonome (défaut, /session + /session/:uuid) : 2 colonnes — lignes
         par phase (gauche) + détail de l'exo sélectionné (droite), = planning-page ;
         empilées sous 900px. Embarqué (.split--stacked, Accueil) : empilement vertical
         quelle que soit la largeur — la séance occupe une demi-colonne du dashboard,
         trop étroite pour un côte-à-côte. */
      .split {
        display: flex;
        gap: var(--page-gutter);
        align-items: flex-start;
      }
      .split__list {
        flex: 1 1 0;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
      }
      .split__detail {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }
      .split--stacked {
        flex-direction: column;
        gap: var(--space-3);
      }
      .split--stacked .split__list,
      .split--stacked .split__detail {
        flex: none;
        width: 100%;
      }
      @media (max-width: 900px) {
        .split {
          flex-direction: column;
        }
        .split__list,
        .split__detail {
          flex: none;
          width: 100%;
        }
      }
      /* Liste d'exercices : grille à colonnes de largeur UNIFORME (chacune flex:1, donc égales et qui
         grandissent avec la largeur comme les lignes de sets à droite) + gap uniforme ; le nom est
         juste plus large (flex:4). Mêmes colonnes pour la légende (.exleg). */
      .exrow {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        height: 44px;
      }
      .exrow__col {
        flex: 1 1 0;
        min-width: 32px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 13px;
        color: var(--app-text-primary);
      }
      /* 1ère colonne (N°) : contenu collé à gauche (pas centré) → le chip/le « N° » s'aligne sur le
         bord du cadre (16px), au niveau des filets et des titres. */
      .exrow__col--order {
        color: var(--app-text-tertiary);
        justify-content: flex-start;
      }
      /* Dernière colonne (statut) : contenu collé à droite → le bouton s'aligne sur le bord droit du
         cadre, au niveau des filets et des titres. */
      .exrow > .exrow__col:last-child {
        justify-content: flex-end;
      }
      /* Numéro d'ordre d'un exo (rows de données) : pastille compacte qui épouse le chiffre ;
         fond second-blue permanent + liseré bleu au survol (= cases Reps/Poids / numéro des séries). */
      .exrow__order-chip {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-width: 28px;
        padding: 2px 6px;
        background: var(--c-second-blue);
        border: 1px solid var(--c-second-blue);
        border-radius: var(--radius-sm);
        color: inherit;
        font: inherit;
        cursor: pointer;
        appearance: none;
        -webkit-appearance: none;
        transition: border-color 0.12s ease;
      }
      .exrow__order-chip:hover,
      .exrow__order-chip:focus-visible {
        border-color: var(--app-primary-action);
      }
      .exrow__col--reps {
        color: var(--c-blue-medium);
      }
      .exrow__col--kg {
        color: var(--c-orange-medium);
      }
      /* Nom : plus large que les colonnes (flex 4), pastille secondBlue cliquable, tronquée « … ».
         Marge à droite : la pastille est pleine (bord net) alors que les valeurs des colonnes sont
         centrées (demi-colonne d'air) → on rééquilibre l'espace pastille→Reps. */
      .exrow__name {
        flex: 4 1 0;
        min-width: 0;
        height: 36px;
        line-height: 36px;
        margin-inline-end: var(--space-3);
        padding: 0 var(--space-3);
        background: var(--c-second-blue);
        border: none;
        border-radius: var(--radius-md);
        color: var(--app-text-primary);
        font-family: var(--font-family-base);
        font-size: var(--font-size-body);
        font-weight: var(--font-weight-medium);
        text-align: left;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        cursor: pointer;
      }
      /* Légende : même grille, hauteur libre, texte 14px (= en-tête du tableau de sets). */
      .exleg {
        height: auto;
        padding-bottom: var(--space-1);
      }
      .exleg .exrow__col,
      .exleg__name {
        font-size: 14px;
        color: var(--app-text-secondary);
      }
      .exleg .exrow__col--reps {
        color: var(--c-blue-medium);
      }
      .exleg .exrow__col--kg {
        color: var(--c-orange-medium);
      }
      .exleg__name {
        flex: 4 1 0;
        min-width: 0;
        margin-inline-end: var(--space-3);
        padding: 0 var(--space-3);
      }
      /* Complétion : les 4 cards en pleine largeur réparties en space-between, puis la barre + boutons.
         Espace tuiles → barre : space-4 (aéré). */
      .completion {
        display: flex;
        flex-direction: column;
        gap: var(--space-4);
      }
      /* Tuiles de complétion : répartition space-evenly dans la largeur du cadre (padding 16px). */
      .completion__summary {
        display: block;
      }
      /* space-between : 1ère tuile collée à gauche, dernière à droite (pas d'espace de bord) →
         les tuiles extrêmes s'alignent sur le padding du cadre. */
      .completion__summary ::ng-deep .sr {
        justify-content: space-between;
        gap: 0;
      }
      .completion__progress {
        display: flex;
        align-items: center;
        gap: var(--space-3);
      }
      .completion__bar {
        flex: 1;
        min-width: 0;
      }
      .completion__actions {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        flex: 0 0 auto;
      }
      /* En-tête détails : tuiles SummaryItem (icône + valeur + label) en secondBlue sur le cadre.
         Tuiles à leur taille de contenu (pas étirées) ; space-between → tuiles extrêmes collées au
         padding du cadre (pas d'espace de bord). */
      .exohead {
        display: flex;
        align-items: center;
        justify-content: space-between;
      }
      .exohead__tile {
        flex: 0 1 auto;
        min-width: 0;
      }
      .exohead ::ng-deep .si {
        background: var(--c-second-blue);
      }
      /* Les cadres thirdBlue (en-tête = titled-divider) sont fournis par app-framed-section :
         complétion (gauche), phases, exo (nom + tuiles), progression, notes, instructions. */
      /* Les 2 tuiles Séries/Exercices (SummaryItem, fond thirdBlue par défaut) → secondBlue dans le cadre. */
      .completion ::ng-deep .si {
        background: var(--c-second-blue);
      }
      /* Rows d'exercice d'une phase : ligne transparente (sur le cadre thirdBlue), séparée par un filet ;
         la boîte du nom passe en secondBlue. Filet seulement ENTRE deux rows d'une même phase (pas sous
         la dernière, qui est suivie du sous-titre de la phase suivante). */
      .phaserow {
        position: relative;
      }
      .phaserow:has(+ .phaserow)::after {
        content: '';
        position: absolute;
        left: 0;
        right: 0;
        bottom: 0;
        height: 1px;
        background: var(--c-second-blue);
      }
      /* Champ note : fond thirdBlue posé dans un cadre thirdBlue → bordure boxBlue (bg-surface) pour le
         détacher (focus = primaryAction). Coins arrondis complets (vs filled top-only par défaut).
         margin-bottom : aère le bas comme les côtés (le cadre n'a que space-2 en vertical vs space-3 latéral). */
      .notesfield {
        display: block;
        margin-bottom: var(--space-1);
      }
      .notesfield ::ng-deep .field__input {
        border: 1px solid var(--app-bg-surface);
        border-radius: var(--radius-md);
      }
      .notesfield ::ng-deep .field__input:focus {
        border-color: var(--app-primary-action);
      }
      .exoprog {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        /* Espace tuiles d'en-tête → barre d'avancement : space-4 (aéré). */
        margin-top: var(--space-4);
      }
      .exoprog__bar {
        flex: 1;
        min-width: 0;
      }
      /* Tableau des séries : en-tête aligné avec app-set-row (mêmes gap/padding/flex que .sr). */
      .settable {
        display: flex;
        flex-direction: column;
      }
      /* Espacement uniforme des titres de sous-section (traits de phase à gauche ; Notes/Instructions
         à droite) : espace au-dessus du divider pour le détacher du contenu précédent, et en dessous
         pour aérer le divider de son contenu — même écart titre→contenu que les en-têtes de cadre
         (≈ 14px : 6px du divider + space-2). */
      .detail-sub {
        margin-top: var(--space-3);
        margin-bottom: var(--space-2);
      }
      /* 1er trait de phase (juste sous la légende = en-tête du tableau) : pas de marge haute — il colle
         à son en-tête au lieu d'être détaché comme les traits entre phases. */
      .exleg + .detail-sub {
        margin-top: 0;
      }
      /* En-tête du tableau des séries (= légende à gauche) : pas de filet sous l'en-tête, et un peu
         d'espace sous les libellés pour ne pas coller à la 1ère ligne. */
      .settable__head {
        display: flex;
        gap: 4px;
        padding: 0 4px var(--space-2);
      }
      /* Rangées de séries séparées par un filet (sauf la dernière) ; padding vertical pour aérer. */
      .settable app-set-row {
        display: block;
        padding: var(--space-1) 0;
      }
      .settable app-set-row:not(:last-child) {
        border-bottom: 1px solid var(--c-second-blue);
      }
      .settable__h {
        text-align: center;
        font-size: 14px;
        color: var(--app-text-secondary);
      }
      .settable__h--idx {
        flex: 1.6;
      }
      .settable__h--reps,
      .settable__h--weight {
        flex: 2;
      }
      .settable__h--ico,
      .settable__h--btn {
        flex: 1.6;
      }
      /* Boutons d'appoint décimal du poids (+0,25 / +0,5 / +0,75) : ajoutés à la partie entière. */
      .fracrow {
        display: flex;
        gap: 6px;
      }
      .fracbtn {
        flex: 1;
        padding: 6px 0;
        border: 1px solid var(--c-second-blue);
        border-radius: var(--radius-sm);
        background: var(--app-bg-surface);
        color: var(--app-text-primary);
        font-family: var(--font-family-base);
        font-size: 13px;
        font-weight: 600;
        cursor: pointer;
        appearance: none;
        -webkit-appearance: none;
      }
      .fracbtn--active {
        background: var(--app-primary-action);
        border-color: var(--app-primary-action);
      }
      /* Instructions : étapes numérotées (déjà dans le cadre thirdBlue). Un poil de padding gauche/bas
         pour ne pas coller au bord ; haut à 0 (collé au divider). */
      .instructions {
        display: flex;
        flex-direction: column;
        gap: 6px;
        padding: 0 0 var(--space-2) var(--space-2);
      }
      .instructions__empty {
        margin: 0;
        text-align: center;
        color: var(--app-text-tertiary);
        font-size: 13px;
      }
      .instructions__step {
        margin: 0;
        font-size: 13px;
        line-height: 20px;
        color: var(--app-text-primary);
      }
      .instructions__num {
        color: var(--app-primary-action);
      }
    `,
  ],
})
export class SessionPage {
  readonly uuid = input.required<string>();
  /** Mode embarqué (hub Home) : masque title bar + bouton retour (le hub fournit les onglets). */
  readonly embedded = input(false);

  private readonly db = inject(AppDb);
  private readonly sync = inject(SyncEngine);
  private readonly router = inject(Router);
  private readonly snackbar = inject(SnackbarService);

  private readonly workouts = toSignal(from(liveQuery(() => this.db.actual_workouts.toArray())), {
    initialValue: [] as LocalActualWorkout[],
  });
  private readonly awExercises = toSignal(
    from(liveQuery(() => this.db.actual_workout_exercises.toArray())),
    { initialValue: [] as LocalActualWorkoutExercise[] },
  );
  private readonly allSets = toSignal(from(liveQuery(() => this.db.actual_workout_sets.toArray())), {
    initialValue: [] as LocalActualWorkoutSet[],
  });
  private readonly exerciseDefs = toSignal(from(liveQuery(() => this.db.exercises.toArray())), {
    initialValue: [] as LocalExercise[],
  });
  private readonly muscles = toSignal(from(liveQuery(() => this.db.muscles.toArray())), {
    initialValue: [] as LocalMuscle[],
  });
  private readonly exMuscles = toSignal(from(liveQuery(() => this.db.exercise_muscles.toArray())), {
    initialValue: [] as LocalExerciseMuscle[],
  });
  private readonly equipments = toSignal(from(liveQuery(() => this.db.equipments.toArray())), {
    initialValue: [] as LocalEquipment[],
  });
  private readonly exEquipment = toSignal(from(liveQuery(() => this.db.exercise_equipment.toArray())), {
    initialValue: [] as LocalExerciseEquipment[],
  });

  protected readonly view = computed(() => {
    const wid = this.uuid();
    const workout = this.workouts().find((w) => w.uuid === wid && !w.pendingDeletion) ?? null;
    if (!workout) return { workout: null as LocalActualWorkout | null, exercises: [] as ExoView[] };

    const exName = new Map(this.exerciseDefs().map((e) => [e.uuid, e.name]));
    const setsByAwe = new Map<string, LocalActualWorkoutSet[]>();
    const maxWeightByAwe = new Map<string, number>();
    for (const s of this.allSets()) {
      if (s.pendingDeletion) continue;
      const arr = setsByAwe.get(s.actualWorkoutExerciseUUID);
      if (arr) arr.push(s);
      else setsByAwe.set(s.actualWorkoutExerciseUUID, [s]);
      if (s.weight > (maxWeightByAwe.get(s.actualWorkoutExerciseUUID) ?? 0)) {
        maxWeightByAwe.set(s.actualWorkoutExerciseUUID, s.weight);
      }
    }

    // « Dernière fois » : poids max de la séance la plus récente STRICTEMENT avant la séance courante
    // où l'exo a été fait (repère d'échelle — il n'y a pas de poids planifié).
    const currentDate = workout.date ?? '';
    const wDate = new Map(this.workouts().map((w) => [w.uuid, w.date]));
    const lastByExercise = new Map<string, { date: string; weight: number }>();
    for (const awe of this.awExercises()) {
      if (awe.pendingDeletion || awe.actualWorkoutUUID === wid) continue;
      const date = wDate.get(awe.actualWorkoutUUID);
      if (!date || date >= currentDate) continue;
      const mw = maxWeightByAwe.get(awe.uuid) ?? 0;
      if (mw <= 0) continue;
      const prev = lastByExercise.get(awe.exerciseUUID);
      if (!prev || date > prev.date) lastByExercise.set(awe.exerciseUUID, { date, weight: mw });
    }

    const exercises: ExoView[] = this.awExercises()
      .filter((e) => e.actualWorkoutUUID === wid && !e.pendingDeletion)
      .slice()
      .sort((a, b) => a.order - b.order)
      .map((e) => {
        const sets = (setsByAwe.get(e.uuid) ?? []).slice().sort((a, b) => a.setOrder - b.setOrder);
        const { min, max } = this.parseReps(e.reps);
        return {
          uuid: e.uuid,
          exerciseUUID: e.exerciseUUID,
          name: exName.get(e.exerciseUUID) ?? '—',
          phase: e.phase,
          order: e.order,
          repsMin: min,
          repsMax: max,
          repsLabel: this.formatReps(e.reps),
          repsShort: max > 0 ? (min === max ? `${min}` : `${min}-${max}`) : ((e.reps ?? '').trim() || '—'),
          sets,
          doneCount: sets.filter((s) => !s.isDropset && s.status.toUpperCase() === 'DONE').length,
          realSetCount: sets.filter((s) => !s.isDropset).length,
          status: e.status,
          synced: e.synced,
          lastWeight: lastByExercise.get(e.exerciseUUID)?.weight ?? null,
          volume: sets
            .filter((s) => s.status.toUpperCase() === 'DONE')
            .reduce((v, s) => v + s.weight * s.reps, 0),
        };
      });

    return { workout, exercises };
  });

  /** 3 phases canoniques (= SessionTab.kt) ; défaut TRAINING (= default DB). */
  protected readonly phases = [
    { key: 'WARMUP', label: 'Échauffement', empty: "Aucun exercice d'échauffement." },
    { key: 'TRAINING', label: 'Entraînement', empty: "Aucun exercice d'entraînement." },
    { key: 'POSTTRAINING', label: 'Récupération', empty: 'Aucun exercice de récupération.' },
  ] as const;

  /** Exercices groupés par phase (WARMUP / TRAINING / POST_TRAINING), ordre conservé. */
  protected readonly byPhase = computed<Record<string, ExoView[]>>(() => {
    const groups: Record<string, ExoView[]> = { WARMUP: [], TRAINING: [], POSTTRAINING: [] };
    for (const e of this.view().exercises) {
      const p = (e.phase || '').toUpperCase().replace(/_/g, '');
      if (p === 'WARMUP') groups['WARMUP'].push(e);
      else if (p === 'POSTTRAINING') groups['POSTTRAINING'].push(e);
      else groups['TRAINING'].push(e);
    }
    return groups;
  });

  /** Avancement de la séance : Σ séries faites / Σ séries + exos terminés. */
  protected readonly stats = computed(() => {
    const exos = this.view().exercises;
    const totalSets = exos.reduce((n, e) => n + e.realSetCount, 0);
    const completedSets = exos.reduce((n, e) => n + e.doneCount, 0);
    const exercisesDone = exos.filter((e) => e.status.toUpperCase() === 'DONE').length;
    const volume = exos.reduce((n, e) => n + e.volume, 0);
    return {
      totalSets,
      completedSets,
      totalExercises: exos.length,
      exercisesDone,
      volume,
      progress: totalSets ? completedSets / totalSets : 0,
    };
  });

  protected readonly summaryItems = computed<SummaryItemData[]>(() => {
    const s = this.stats();
    const items: SummaryItemData[] = [
      { icon: 'fitness_center', value: `${s.completedSets} / ${s.totalSets}`, label: 'Séries', iconTint: 'var(--app-primary-action)' },
      { icon: 'exercise', value: `${s.exercisesDone} / ${s.totalExercises}`, label: 'Exercices', iconTint: 'var(--c-medium-green)' },
      { icon: 'stacked_bar_chart', value: this.formatVol(s.volume), label: 'Volume', iconTint: 'var(--c-light-gray-blue)' },
    ];
    const date = this.view().workout?.date;
    if (date) {
      items.push({ icon: 'calendar_today', value: this.formatDateShort(date), label: '', iconTint: 'var(--c-blue-medium)' });
    }
    return items;
  });

  /** Master-detail : exo affiché dans le panneau de droite (défaut = 1er exo de la séance). */
  protected readonly selectedExo = signal<string | null>(null);
  protected readonly effectiveExoUuid = computed(
    () => this.selectedExo() ?? this.view().exercises[0]?.uuid ?? null,
  );
  protected readonly selectedExoView = computed(
    () => this.view().exercises.find((e) => e.uuid === this.effectiveExoUuid()) ?? null,
  );

  /** Définition de l'exercice sélectionné (description + instructions), via son exerciseUUID. */
  protected readonly selectedExoDef = computed(() => {
    const ex = this.selectedExoView();
    if (!ex) return null;
    return this.exerciseDefs().find((d) => d.uuid === ex.exerciseUUID && !d.pendingDeletion) ?? null;
  });

  /** Instructions (lecture seule) de l'exercice sélectionné — étapes non vides. */
  protected readonly selectedExoInstructions = computed(() =>
    (this.selectedExoDef()?.instructions ?? []).filter((s) => s.trim().length > 0),
  );

  /** Note de l'exercice (= description), éditable inline ; se ré-initialise au changement d'exo. */
  protected readonly noteDraft = linkedSignal(() => this.selectedExoDef()?.description ?? '');

  /** Avancement de l'exo (séries réelles faites / total réel, dropsets exclus) pour la barre. */
  protected exProgress(ex: ExoView): number {
    return ex.realSetCount ? ex.doneCount / ex.realSetCount : 0;
  }

  /** Tuiles N° / Séries / Reps de l'en-tête exo (mêmes SummaryItem que la complétion). */
  protected exoStats(ex: ExoView): SummaryItemData[] {
    return [
      {
        icon: 'fitness_center',
        value: `${ex.doneCount} / ${ex.realSetCount}`,
        label: 'Séries',
        iconTint: 'var(--app-primary-action)',
      },
      { icon: 'repeat', value: ex.repsLabel, label: 'Reps', iconTint: 'var(--c-medium-green)' },
      {
        icon: 'monitor_weight',
        value: ex.lastWeight !== null ? String(ex.lastWeight) : '—',
        label: 'Charge',
        iconTint: 'var(--c-orange-medium)',
      },
      { icon: 'stacked_bar_chart', value: this.formatVol(ex.volume), label: 'Volume', iconTint: 'var(--c-light-gray-blue)' },
    ];
  }

  /** Volume compact pour les tuiles (k au-delà de 1000). */
  protected formatVol(v: number): string {
    return v >= 1000 ? `${(v / 1000).toFixed(1)}k` : String(Math.round(v));
  }

  /** Date courte en texte « 1 juil. » depuis un ISO (pour la tuile Date) — pas de « / » (sinon lue
   *  comme un ratio x/y : jour coloré, espaces ajoutés). */
  protected formatDateShort(iso: string): string {
    const d = iso.slice(0, 10).split('-');
    if (d.length !== 3) return iso;
    const months = ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'];
    const month = months[parseInt(d[1], 10) - 1] ?? d[1];
    return `${parseInt(d[2], 10)} ${month}`;
  }

  /** Exo terminé = toutes ses séries faites. */
  protected exDone(ex: ExoView): boolean {
    return ex.sets.length > 0 && ex.doneCount >= ex.sets.length;
  }

  /** Sauvegarde la note de l'exercice (description) à la perte de focus, si modifiée (+ sync). */
  protected async saveNote(): Promise<void> {
    const def = this.selectedExoDef();
    if (!def) return;
    const next = this.noteDraft().trim();
    if (next === (def.description ?? '').trim()) return;
    await this.db.exercises.update(def.uuid, {
      description: next.length ? next : null,
      synced: false,
      updatedAt: new Date().toISOString(),
    });
    this.triggerSync();
  }

  /** Ouvre la page Exercice (stats + infos) de l'exo sélectionné (= bouton monitoring Android). */
  protected goToExercise(ex: ExoView): void {
    void this.router.navigate(['/exercise', ex.exerciseUUID]);
  }

  // Options d'un exercice (clic sur le nom) — = ExerciseOptionsBottomSheet.kt.
  protected readonly exoForOptions = signal<ExoView | null>(null);
  /** Actions du sheet : Monter/Descendre (réordo DANS la phase, si possible), puis voir / retirer. */
  protected readonly exoOptions = computed<SheetAction[]>(() => {
    const ex = this.exoForOptions();
    const actions: SheetAction[] = [];
    if (ex) {
      const group = this.phaseGroupOf(ex);
      const i = group.findIndex((e) => e.uuid === ex.uuid);
      if (i > 0) actions.push({ label: 'Monter', icon: 'arrow_upward', color: 'var(--c-blue-medium)' });
      if (i >= 0 && i < group.length - 1) {
        actions.push({ label: 'Descendre', icon: 'arrow_downward', color: 'var(--c-blue-medium)' });
      }
    }
    actions.push({ label: "Voir l'exercice", icon: 'visibility', color: 'var(--c-blue-medium)' });
    actions.push({ label: 'Retirer de la séance', icon: 'delete_forever', color: 'var(--c-red-medium)' });
    return actions;
  });

  // Options d'une série (clic sur le N°) — = SetOptionsBottomSheet.kt (4 actions, même ordre).
  protected readonly setForOptions = signal<LocalActualWorkoutSet | null>(null);
  protected readonly showStatusSheet = signal(false);
  protected readonly setOptions: SheetAction[] = [
    { label: 'Série bonus', icon: 'add', color: 'var(--app-primary-action)' },
    { label: 'Dropset', icon: 'add_link', color: 'color-mix(in srgb, var(--app-primary-action) 75%, transparent)' },
    { label: 'Changer le statut', icon: 'info', color: 'var(--app-selected-fill)' },
    { label: 'Superset', icon: 'join', color: 'color-mix(in srgb, var(--app-selected-fill) 75%, transparent)' },
  ];
  // Statuts d'une série — mêmes icônes/couleurs qu'Android (ChangeSetStatusDialog.kt) :
  // help/textTertiary · arrow_progress(→arrow_circle_up web)/orange · check_circle/vert · cancel/rouge.
  protected readonly setStatusOptions: StatusOption[] = [
    { value: 'NOT_STARTED', label: 'Non commencé', icon: 'help', color: 'var(--app-text-tertiary)' },
    { value: 'IN_PROGRESS', label: 'En cours', icon: 'arrow_circle_up', color: 'var(--c-orange-medium)' },
    { value: 'DONE', label: 'Terminé', icon: 'check_circle', color: 'var(--c-medium-green)' },
    { value: 'SKIPPED', label: 'Ignoré', icon: 'cancel', color: 'var(--c-red-medium)' },
  ];

  /** Pill statut (= SessionExerciseRow.kt) : couleur + icône selon l'état de l'exercice. */
  protected statusBg(status: string): string {
    switch (status.toUpperCase()) {
      case 'DONE':
        return 'var(--c-medium-green)';
      case 'IN_PROGRESS':
        return 'var(--c-orange-medium)';
      case 'SKIPPED':
        return 'var(--c-red-medium)';
      default:
        return 'var(--c-blue-medium)';
    }
  }
  protected statusIcon(status: string): string {
    switch (status.toUpperCase()) {
      case 'DONE':
        return 'check';
      case 'IN_PROGRESS':
        return 'arrow_circle_up';
      case 'SKIPPED':
        return 'cancel';
      default:
        return 'keyboard_arrow_right';
    }
  }

  protected openExerciseOptions(ex: ExoView): void {
    this.exoForOptions.set(ex);
  }

  protected onExoOption(label: string): void {
    const ex = this.exoForOptions();
    this.exoForOptions.set(null);
    if (!ex) return;
    if (label === 'Monter') void this.moveExo(ex, -1);
    else if (label === 'Descendre') void this.moveExo(ex, 1);
    else if (label.startsWith('Retirer')) void this.removeExercise(ex);
    else void this.router.navigate(['/exercises']);
  }

  // Déplacement d'un exo (clic sur le numéro de la colonne de gauche) — Monter/Descendre dans la
  // phase + Changer de section (toujours dispo → le sheet a toujours au moins une action).
  protected readonly orderForOptions = signal<ExoView | null>(null);
  protected readonly changingPhaseExo = signal<ExoView | null>(null);
  protected readonly orderOptions = computed<SheetAction[]>(() => {
    const ex = this.orderForOptions();
    if (!ex) return [];
    const group = this.phaseGroupOf(ex);
    const i = group.findIndex((e) => e.uuid === ex.uuid);
    const actions: SheetAction[] = [];
    if (i > 0) actions.push({ label: 'Monter', icon: 'arrow_upward', color: 'var(--c-blue-medium)' });
    if (i >= 0 && i < group.length - 1) actions.push({ label: 'Descendre', icon: 'arrow_downward', color: 'var(--c-blue-medium)' });
    actions.push({ label: 'Changer de section', icon: 'swap_horiz', color: 'var(--app-primary-action)' });
    return actions;
  });

  protected openOrderOptions(ex: ExoView): void {
    this.orderForOptions.set(ex);
  }

  protected onOrderOption(label: string): void {
    const ex = this.orderForOptions();
    this.orderForOptions.set(null);
    if (!ex) return;
    if (label === 'Monter') void this.moveExo(ex, -1);
    else if (label === 'Descendre') void this.moveExo(ex, 1);
    else if (label === 'Changer de section') this.changingPhaseExo.set(ex);
  }

  /** Phase choisie pour un exo existant → le déplace dans cette section (no-op si identique). */
  protected onChangePhase(phase: string): void {
    const ex = this.changingPhaseExo();
    this.changingPhaseExo.set(null);
    if (!ex || this.samePhase(ex.phase, phase)) return;
    void this.moveExoToPhase(ex, phase);
  }

  /** Déplace un exo dans une autre section : placé à la fin du bloc de cette phase, puis renumérote
   *  1..N par (rang de phase, ordre) pour garder les 3 blocs contigus et ordonnés. */
  private async moveExoToPhase(ex: ExoView, targetPhase: string): Promise<void> {
    const wid = this.uuid();
    const list = this.awExercises().filter((e) => e.actualWorkoutUUID === wid && !e.pendingDeletion);
    const targetRank = this.phaseRank(targetPhase);
    // Tri global (rang de phase, ordre), l'exo déplacé mis en dernier de son nouveau bloc.
    const ordered = list
      .map((e) => ({
        e,
        rank: e.uuid === ex.uuid ? targetRank : this.phaseRank(e.phase),
        ord: e.uuid === ex.uuid ? Number.MAX_SAFE_INTEGER : e.order,
      }))
      .sort((a, b) => a.rank - b.rank || a.ord - b.ord);
    const now = new Date().toISOString();
    let i = 0;
    for (const { e } of ordered) {
      const newOrder = ++i;
      if (e.uuid === ex.uuid) {
        await this.db.actual_workout_exercises.update(e.uuid, { order: newOrder, phase: targetPhase, synced: false, updatedAt: now });
      } else if (e.order !== newOrder) {
        await this.db.actual_workout_exercises.update(e.uuid, { order: newOrder, synced: false, updatedAt: now });
      }
    }
    this.triggerSync();
  }

  /** Deux phases équivalentes (insensible casse + underscores : POST_TRAINING == POSTTRAINING). */
  private samePhase(a: string, b: string): boolean {
    return (a ?? '').toUpperCase().replace(/_/g, '') === (b ?? '').toUpperCase().replace(/_/g, '');
  }

  /** Rang canonique d'une phase (WARMUP=0 < TRAINING=1 < POSTTRAINING=2) ; défaut TRAINING (= default DB). */
  private phaseRank(phase: string): number {
    const p = (phase ?? '').toUpperCase().replace(/_/g, '');
    const i = this.phases.findIndex((ph) => ph.key === p);
    return i >= 0 ? i : 1;
  }

  /** Exos de la même phase que `ex`, triés par ordre (= un « groupe » réordonnable). */
  private phaseGroupOf(ex: ExoView): ExoView[] {
    return this.view()
      .exercises.filter((e) => this.samePhase(e.phase, ex.phase))
      .sort((a, b) => a.order - b.order);
  }

  /** Déplace l'exo d'un cran (dir -1 = monter, +1 = descendre) dans sa phase, par échange d'ordre. */
  private async moveExo(ex: ExoView, dir: -1 | 1): Promise<void> {
    const group = this.phaseGroupOf(ex);
    const i = group.findIndex((e) => e.uuid === ex.uuid);
    const j = i + dir;
    if (i < 0 || j < 0 || j >= group.length) return;
    const now = new Date().toISOString();
    await this.db.actual_workout_exercises.update(group[i].uuid, { order: group[j].order, synced: false, updatedAt: now });
    await this.db.actual_workout_exercises.update(group[j].uuid, { order: group[i].order, synced: false, updatedAt: now });
    this.triggerSync();
  }

  /** Retire l'exercice de la séance (tombstone local + sync). */
  private async removeExercise(ex: ExoView): Promise<void> {
    await this.db.actual_workout_exercises.update(ex.uuid, {
      pendingDeletion: true,
      synced: false,
      updatedAt: new Date().toISOString(),
    });
    if (this.selectedExo() === ex.uuid) this.selectedExo.set(null);
    this.triggerSync();
    this.snackbar.success(`« ${ex.name} » retiré de la séance.`);
  }

  protected readonly editing = signal<LocalActualWorkoutSet | null>(null);
  protected readonly editReps = signal('');
  protected readonly editWeight = signal('');
  protected readonly editNote = signal('');

  /** Reps en cours d'édition (nombre) pour le sélecteur horizontal. */
  protected readonly editRepsNum = computed(() => {
    const n = parseInt(this.editReps(), 10);
    return isNaN(n) ? 0 : n;
  });
  /** Partie ENTIÈRE du poids (= valeur du sélecteur horizontal). La décimale vient des boutons d'appoint. */
  protected readonly editWeightNum = computed(() => {
    const n = parseFloat(this.editWeight().replace(',', '.'));
    return isNaN(n) ? 0 : Math.floor(n);
  });
  /** Appoint décimal actif (0 / 0.25 / 0.5 / 0.75), déduit du poids courant (snap au quart). */
  protected readonly editWeightFrac = computed(() => {
    const n = parseFloat(this.editWeight().replace(',', '.'));
    if (isNaN(n)) return 0;
    return Math.round((n - Math.floor(n)) * 4) / 4;
  });
  /** Boutons d'appoint décimal du poids (ajoutés à la partie entière à l'enregistrement). */
  protected readonly weightFracs = [
    { value: 0, label: '0' },
    { value: 0.25, label: '+0,25' },
    { value: 0.5, label: '+0,5' },
    { value: 0.75, label: '+0,75' },
  ];
  /** Plage recommandée de reps (cible de l'exo sélectionné) ; null si pas de cible (pas de rouge). */
  protected readonly repsTargetMin = computed(() => {
    const ex = this.selectedExoView();
    return ex && ex.repsMax > 0 ? ex.repsMin : null;
  });
  protected readonly repsTargetMax = computed(() => {
    const ex = this.selectedExoView();
    return ex && ex.repsMax > 0 ? ex.repsMax : null;
  });

  // Ajout d'exercice (= ExercisePickerBottomSheet -> PhasePickerDialog Android).
  protected readonly showAddSheet = signal(false);
  protected readonly pendingExerciseUuid = signal<string | null>(null);

  /** exerciseUUID → groupes musculaires distincts (via exercise_muscles + muscles). */
  private readonly groupsByExercise = computed(() => {
    const groupByMuscle = new Map(
      this.muscles().filter((m) => !m.pendingDeletion && m.muscleGroup).map((m) => [m.uuid, m.muscleGroup!]),
    );
    const map = new Map<string, Set<string>>();
    for (const em of this.exMuscles()) {
      if (em.pendingDeletion) continue;
      const g = groupByMuscle.get(em.muscleUUID);
      if (!g) continue;
      let set = map.get(em.exerciseUUID);
      if (!set) {
        set = new Set<string>();
        map.set(em.exerciseUUID, set);
      }
      set.add(g);
    }
    return map;
  });

  /** exerciseUUID → noms de matériel distincts (via exercise_equipment + equipments). */
  private readonly equipmentsByExercise = computed(() => {
    const nameByUuid = new Map(
      this.equipments().filter((q) => !q.pendingDeletion).map((q) => [q.uuid, q.name]),
    );
    const map = new Map<string, Set<string>>();
    for (const ee of this.exEquipment()) {
      if (ee.pendingDeletion) continue;
      const n = nameByUuid.get(ee.equipmentUUID);
      if (!n) continue;
      let set = map.get(ee.exerciseUUID);
      if (!set) {
        set = new Set<string>();
        map.set(ee.exerciseUUID, set);
      }
      set.add(n);
    }
    return map;
  });

  /** Exercices pas encore dans la séance — alimente le picker (étiquettes muscle + matériel). */
  protected readonly addableExercises = computed<ExercisePickerItem[]>(() => {
    const wid = this.uuid();
    const inSession = new Set(
      this.awExercises()
        .filter((e) => e.actualWorkoutUUID === wid && !e.pendingDeletion)
        .map((e) => e.exerciseUUID),
    );
    const groups = this.groupsByExercise();
    const equip = this.equipmentsByExercise();
    return this.exerciseDefs()
      .filter((e) => !inSession.has(e.uuid))
      .map((e) => ({
        uuid: e.uuid,
        name: e.name,
        equipments: [...(equip.get(e.uuid) ?? [])],
        muscleTags: [...(groups.get(e.uuid) ?? [])],
      }));
  });

  /** Options du filtre muscle (groupes présents dans les exos ajoutables) + « Tous » en tête. */
  protected readonly muscleFilterOptions = computed<string[]>(() => {
    const set = new Set<string>();
    for (const e of this.addableExercises()) for (const t of e.muscleTags ?? []) set.add(t);
    return ['Tous', ...[...set].sort((a, b) => a.localeCompare(b))];
  });

  /** Options du filtre matériel (matériels présents dans les exos ajoutables) + « Tous » en tête. */
  protected readonly equipmentFilterOptions = computed<string[]>(() => {
    const set = new Set<string>();
    for (const e of this.addableExercises()) for (const t of e.equipments) set.add(t);
    return ['Tous', ...[...set].sort((a, b) => a.localeCompare(b))];
  });


  protected toRow(s: LocalActualWorkoutSet, ex: ExoView): SetRowData {
    // Numéro affiché = rang parmi les séries RÉELLES (les dropsets font partie de leur série
    // parente, ils ne consomment pas de numéro → 1, ↳, 2, 3). setOrder en base inchangé.
    let displayNumber = 0;
    if (!s.isDropset) {
      for (const x of ex.sets) {
        if (!x.isDropset) displayNumber++;
        if (x.uuid === s.uuid) break;
      }
    }
    return {
      setOrder: displayNumber,
      reps: s.reps,
      weight: s.weight,
      status: s.status,
      isDropset: s.isDropset,
      pendingDeletion: s.pendingDeletion,
      hasNote: !!s.notes && s.notes.length > 0,
    };
  }

  /** Action choisie dans la feuille d'options de la série (= SetOptionsBottomSheet.kt). */
  protected onSetOption(label: string): void {
    const s = this.setForOptions();
    if (!s) return;
    switch (label) {
      case 'Série bonus':
        this.setForOptions.set(null);
        void this.addSet(this.selectedExoView()!); // série normale ajoutée en fin (= insertBonusSet)
        break;
      case 'Dropset':
        this.setForOptions.set(null);
        void this.addDropsetAfter(s);
        break;
      case 'Changer le statut':
        this.showStatusSheet.set(true); // garde setForOptions pour la sous-feuille
        break;
      case 'Superset':
        this.setForOptions.set(null);
        this.snackbar.info('Superset : bientôt disponible.'); // stub (= TODO côté Android)
        break;
    }
  }

  /** Statut confirmé dans le StatusPickerDialog (= ChangeSetStatusDialog.kt) — `value` = code wire. */
  protected async onPickSetStatus(value: string): Promise<void> {
    const s = this.setForOptions();
    this.showStatusSheet.set(false);
    this.setForOptions.set(null);
    if (!s || !value) return;
    await this.patchSet(s.uuid, { status: value });
  }

  /** Insère un dropset juste après la série cliquée (décale les suivantes), reps/poids copiés comme base. */
  private async addDropsetAfter(base: LocalActualWorkoutSet): Promise<void> {
    const ex = this.selectedExoView();
    if (!ex) return;
    const now = new Date().toISOString();
    for (const x of ex.sets) {
      if (!x.pendingDeletion && x.setOrder > base.setOrder) {
        await this.db.actual_workout_sets.update(x.uuid, { setOrder: x.setOrder + 1, synced: false, updatedAt: now });
      }
    }
    await this.db.actual_workout_sets.put({
      uuid: uuidv4(),
      actualWorkoutExerciseUUID: ex.uuid,
      setOrder: base.setOrder + 1,
      reps: base.reps,
      weight: base.weight,
      isDropset: true,
      notes: null,
      recommendation: null,
      status: 'NOT_STARTED',
      updatedAt: now,
      synced: false,
      pendingDeletion: false,
    });
    this.triggerSync();
  }

  protected openEdit(s: LocalActualWorkoutSet): void {
    this.editing.set(s);
    this.editReps.set(String(s.reps));
    this.editWeight.set(String(s.weight));
    this.editNote.set(s.notes ?? '');
  }

  protected async saveEdit(): Promise<void> {
    const s = this.editing();
    if (!s) return;
    const reps = parseInt(this.editReps(), 10);
    const weight = parseFloat(this.editWeight().replace(',', '.'));
    const note = this.editNote().trim();
    await this.patchSet(s.uuid, {
      reps: Number.isNaN(reps) ? 0 : reps,
      weight: Number.isNaN(weight) ? 0 : weight,
      notes: note || null,
    });
    this.editing.set(null);
  }

  protected async deleteSet(s: LocalActualWorkoutSet): Promise<void> {
    await this.patchSet(s.uuid, { pendingDeletion: true });
  }

  protected async addSet(ex: ExoView): Promise<void> {
    const maxOrder = ex.sets.reduce((m, s) => Math.max(m, s.setOrder), 0);
    const row: LocalActualWorkoutSet = {
      uuid: uuidv4(),
      actualWorkoutExerciseUUID: ex.uuid,
      setOrder: maxOrder + 1,
      reps: 0,
      weight: 0,
      isDropset: false,
      notes: null,
      recommendation: null,
      status: 'NOT_STARTED',
      updatedAt: new Date().toISOString(),
      synced: false,
      pendingDeletion: false,
    };
    await this.db.actual_workout_sets.put(row);
    this.triggerSync();
  }

  protected async toggleDone(w: LocalActualWorkout): Promise<void> {
    await this.db.actual_workouts.update(w.uuid, {
      isDone: !w.isDone,
      synced: false,
      updatedAt: new Date().toISOString(),
    });
    this.triggerSync();
  }

  /** Sync manuel (icône cloud, = onSyncClick Android) avec retour snackbar. */
  protected onSync(): void {
    void this.sync
      .syncAll()
      .then(() => this.snackbar.success('Séance synchronisée.'))
      .catch(() => this.snackbar.error('Échec de la synchronisation.'));
  }

  /** Exercice choisi dans le picker -> on demande la phase. */
  protected onPickExercise(exerciseUUID: string): void {
    this.showAddSheet.set(false);
    this.pendingExerciseUuid.set(exerciseUUID);
  }

  /** Phase choisie -> crée l'actual_workout_exercise + ses séries recommandées (= addExerciseToPhase). */
  protected async onPickPhase(phase: string): Promise<void> {
    const exerciseUUID = this.pendingExerciseUuid();
    this.pendingExerciseUuid.set(null);
    if (!exerciseUUID) return;
    const def = this.exerciseDefs().find((e) => e.uuid === exerciseUUID);
    if (!def) return;

    const wid = this.uuid();
    const sessionAwe = this.awExercises().filter((e) => e.actualWorkoutUUID === wid && !e.pendingDeletion);
    const n = def.recommendedSets ?? 3;
    const aweUuid = uuidv4();
    const now = new Date().toISOString();

    // Insère à la FIN du BLOC de la phase ciblée (= addExerciseToPhase Android). Les 3 phases sont des
    // blocs contigus et ordonnés (échauffement < entraînement < récupération) : l'ordre d'insertion =
    // max des ordres de tous les exos dont la phase précède ou égale la phase ciblée, + 1. On calcule
    // donc sur le bloc « phase ciblée et antérieures » (pas seulement la phase ciblée) pour que
    // l'insertion tombe au bon endroit même quand la phase ciblée est vide (sinon l'exo prendrait 1 et
    // passerait devant les phases antérieures). Puis on décale tous les exos d'ordre >= insertOrder.
    const targetRank = this.phaseRank(phase);
    const insertOrder =
      sessionAwe.filter((e) => this.phaseRank(e.phase) <= targetRank).reduce((m, e) => Math.max(m, e.order), 0) + 1;
    for (const e of sessionAwe) {
      if (e.order >= insertOrder) {
        await this.db.actual_workout_exercises.update(e.uuid, { order: e.order + 1, synced: false, updatedAt: now });
      }
    }

    const awe: LocalActualWorkoutExercise = {
      uuid: aweUuid,
      actualWorkoutUUID: wid,
      exerciseUUID,
      sets: n,
      reps: def.recommendedReps ?? '8-12',
      phase,
      status: 'NOT_STARTED',
      order: insertOrder,
      addedManually: true,
      updatedAt: now,
      synced: false,
      pendingDeletion: false,
    };
    const sets: LocalActualWorkoutSet[] = Array.from({ length: n }, (_, i) => ({
      uuid: uuidv4(),
      actualWorkoutExerciseUUID: aweUuid,
      setOrder: i + 1,
      reps: 0,
      weight: 0,
      isDropset: false,
      notes: null,
      recommendation: null,
      status: 'NOT_STARTED',
      updatedAt: now,
      synced: false,
      pendingDeletion: false,
    }));
    await this.db.actual_workout_exercises.put(awe);
    await this.db.actual_workout_sets.bulkPut(sets);
    this.triggerSync();
    this.snackbar.success(`« ${def.name} » ajouté.`);
  }

  private async patchSet(uuid: string, changes: Partial<LocalActualWorkoutSet>): Promise<void> {
    await this.db.actual_workout_sets.update(uuid, {
      ...changes,
      synced: false,
      updatedAt: new Date().toISOString(),
    });
    this.triggerSync();
  }

  private triggerSync(): void {
    void this.sync.syncAll().catch(() => undefined);
  }

  private parseReps(r: string): { min: number; max: number } {
    const m = r.match(/(\d+)\s*-\s*(\d+)/);
    if (m) return { min: +m[1], max: +m[2] };
    const n = parseInt(r, 10);
    return Number.isNaN(n) ? { min: 0, max: 0 } : { min: n, max: n };
  }

  /** Reps formatées pour l'en-tête détails (= Android : "8-12" → "8 - 12"), tiret si vide. */
  private formatReps(reps: string): string {
    const s = (reps ?? '').trim();
    if (!s) return '-';
    return s
      .split('-')
      .map((p) => p.trim())
      .join(' - ');
  }
}
