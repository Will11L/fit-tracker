import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { LocalNutritionGoal } from '@core/models/nutrition-goal.model';
import { AuthService } from '@core/auth/auth.service';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { ListFrame } from '@designsystem/common_components/list-frame';
import { ListRow } from '@designsystem/common_components/list-row';
import { EmptyListRow } from '@designsystem/common_components/empty-list-row';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { ActionIconWithTextButton } from '@designsystem/common_components/action-icon-with-text-button';
import { FormDialog } from '@designsystem/common_components/form-dialog';
import { ConfirmationDialog } from '@designsystem/common_components/confirmation-dialog';
import { CustomDatePickerDialog } from '@designsystem/common_components/custom-date-picker-dialog';
import { DateField } from '@designsystem/common_components/date-field';
import { AppIcon } from '@designsystem/icons/app-icon';
import { WheelPicker } from '@designsystem/common_components/wheel-picker';
import { DonutChartComponent, type DonutSlice } from '@designsystem/common_components/donut-chart';
import { RadarChartComponent } from '@designsystem/common_components/radar-chart';
import {
  OptionsBottomSheet,
  type SheetAction,
} from '@designsystem/common_components/options-bottom-sheet';
import { SyncEngine } from '@core/sync/sync-engine';
import { NutritionGoalRepository } from './nutrition-goal.repository';
import { MealRepository } from './meal.repository';
import { macroRadarData, type MacroAmounts, type MacroTargets } from './nutrition-summary-panel';
import { macroRingViews } from './macro-rings-chart';
import { ConcentricRingsChart } from '@designsystem/common_components/concentric-rings-chart';
import { addDays, fiberTargetG, todayIso } from './journal-utils';
import { deriveGoalFromMacros } from './goal-macros';
import { fiberDensity, macroKcalBreakdown, macroPerKg } from './goal-analysis';
import { MACRO_COLOR, MACRO_LABEL, MACRO_ICON } from './macro-colors';
import { aggregateNutrition } from './nutrition-stats-utils';

/**
 * Objectifs nutrition (`/nutrition/goals`, V5 NUTRITION_DESIGN §5.5) — cible active du jour
 * (kcal + P/G/L) + analyse (répartition kcal en donut, profil radar, comparatif vs réel sur 7 j,
 * indicateurs dérivés) + historique des cibles par `effectiveFrom` (§3.7 : la cible active un jour
 * J = celle avec le plus grand effectiveFrom ≤ J — les stats passées restent comparées à la cible
 * qui était active ce jour-là). Code couleur par macro (macro-colors.ts) sur toute la page.
 * Actions : nouvelle cible, modifier, supprimer.
 */
@Component({
  selector: 'app-nutrition-goals-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ScreenTitleBar,
    TitledDivider,
    EmptyListRow,
    ActionIconButton,
    ActionIconWithTextButton,
    FormDialog,
    ConfirmationDialog,
    CustomDatePickerDialog,
    DateField,
    WheelPicker,
    AppIcon,
    DonutChartComponent,
    RadarChartComponent,
    ConcentricRingsChart,
    OptionsBottomSheet,
    ListFrame,
    ListRow,
  ],
  template: `
    <section class="page">
      <app-screen-title-bar title="Objectifs nutrition" />

      <div class="page__body">
        <!-- Grille 3 colonnes égales × 2 lignes ; chaque ligne = la moitié de la hauteur dispo (header
             déduit). Les panneaux « larges » occupent 2 colonnes (.gcell--wide) → le bord interne tombe
             à 1/3 sur L1 et à 2/3 sur L2 (proportions inversées entre les 2 lignes).
             L1 (1/3 · 2/3) : Cible active · Répartition ; L2 (2/3 · 1/3) : Profil macros · Historique. -->

        <!-- L1 · C1 (1/3) : Cible active. -->
        <div class="gcell">
          <app-titled-divider title="Cible active" />
          <div class="active">
            @if (activeGoal(); as g) {
              <!-- Cadre du haut : valeurs de la cible en tuiles (gros chiffres) — Calories en grand
                   (pleine largeur) puis 2×2 macros. -->
              <div class="active__values">
                <div class="active__tile active__tile--kcal" [style.border-color]="macroColor.kcal">
                  <span class="active__tile-num" [style.color]="macroColor.kcal">{{ round(g.kcal) }}</span>
                  <span class="active__tile-label">{{ macroLabel.kcal }}</span>
                </div>
                <div class="active__tile active__tile--tl" [style.border-color]="macroColor.protein">
                  <span class="active__tile-num" [style.color]="macroColor.protein">{{ round(g.proteinG) }} g</span>
                  <span class="active__tile-label">{{ macroLabel.protein }}</span>
                </div>
                <div class="active__tile active__tile--tr" [style.border-color]="macroColor.carbs">
                  <span class="active__tile-num" [style.color]="macroColor.carbs">{{ round(g.carbsG) }} g</span>
                  <span class="active__tile-label">{{ macroLabel.carbs }}</span>
                </div>
                <div class="active__tile active__tile--bl" [style.border-color]="macroColor.fat">
                  <span class="active__tile-num" [style.color]="macroColor.fat">{{ round(g.fatG) }} g</span>
                  <span class="active__tile-label">{{ macroLabel.fat }}</span>
                </div>
                <div class="active__tile active__tile--br" [style.border-color]="macroColor.fiber">
                  <span class="active__tile-num" [style.color]="macroColor.fiber">{{ round(fiberTarget(g.kcal)) }} g</span>
                  <span class="active__tile-label">{{ macroLabel.fiber }}</span>
                </div>
              </div>
              <!-- Cadre du bas : « Depuis le [date] » à gauche, « + Nouvelle cible » à droite. -->
              <div class="active__bar">
                <span class="active__since">Depuis le <span class="date-value">{{ formatDate(g.effectiveFrom) }}</span></span>
                <app-action-icon-with-text-button icon="add" text="Nouvelle cible" [backgroundColor]="'var(--c-first-blue)'" (clicked)="openCreate()" />
              </div>
            } @else {
              <!-- Pas de cible : cadre invite + bouton « + Nouvelle cible ». -->
              <div class="active__bar">
                <span class="active__empty">Aucune cible active — définis ta première cible quotidienne.</span>
                <app-action-icon-with-text-button icon="add" text="Nouvelle cible" [backgroundColor]="'var(--c-first-blue)'" (clicked)="openCreate()" />
              </div>
            }
          </div>
        </div>

        <!-- L1 · C2 (2/3) : Répartition des calories. -->
        <div class="gcell gcell--wide">
          <app-titled-divider title="Répartition des calories" />
          @if (activeGoal(); as g) {
            <div class="analysis-row">
              <!-- Cercle : donut + légende part en % par macro. -->
              <div class="breakdown__donut analysis-row__item">
                <app-donut-chart
                  class="breakdown__donut-chart"
                  [slices]="donutSlices()"
                  [fill]="true"
                  [height]="190"
                  [centerLabel]="round(g.kcal).toString()"
                  centerSub="kcal"
                  emptyText="—"
                />
              </div>
              <!-- g / kg de poids de corps (+ densité fibres) : 2×2 tuiles (grand chiffre coloré + icône). -->
              <div class="breakdown__list analysis-row__item">
                <div class="bwtiles">
                  @for (t of bodyweightTiles(); track t.key) {
                    <div class="bwtile">
                      <div class="bwtile__top">
                        <span class="bwtile__num" [style.color]="macroColor[t.key]">{{ t.num }}</span>
                        <span class="bwtile__unit">{{ t.unit }}</span>
                      </div>
                      <div class="bwtile__bot">
                        <app-icon [name]="macroIcon[t.key]" [size]="14" [color]="macroColor[t.key]" />
                        <span class="bwtile__label">{{ macroLabel[t.key] }}</span>
                      </div>
                    </div>
                  }
                </div>
                @if (!hasWeight()) {
                  <span class="breakdown__list-hint">
                    Renseigne ton poids dans le profil pour les apports rapportés au poids (g/kg).
                  </span>
                }
              </div>
            </div>
          } @else {
            <app-empty-list-row text="Définis une cible pour voir la répartition." icon="insights" />
          }
        </div>

        <!-- L2 · C1 (2/3) : Profil macros (radar · anneaux). -->
        <div class="gcell gcell--wide">
          <app-titled-divider title="Profil macros" />
          @if (activeGoal()) {
            <div class="analysis-row">
              <div class="profile-cell analysis-row__item">
                <app-radar-chart
                  [axes]="comparisonRadar().axes"
                  [series]="comparisonRadar().series"
                  [fill]="true"
                  [showLegend]="false"
                  [showAxisPercent]="true"
                  emptyText="—"
                />
                <span class="profile__caption">Moyenne des 7 derniers jours comparée à la cible.</span>
              </div>
              <div class="profile-cell analysis-row__item">
                <app-concentric-rings-chart
                  [rings]="macroRings()"
                  [centerText]="macroRingsCenterText()"
                  [centerColor]="macroColor.kcal"
                  [fitHeight]="true"
                />
              </div>
            </div>
          } @else {
            <app-empty-list-row text="Définis une cible pour voir le profil macros." icon="insights" />
          }
        </div>

        <!-- L2 · C2 (1/3) : Historique des cibles. -->
        <div class="gcell gcell--list">
          <app-titled-divider title="Historique" />
          @if (history().length === 0) {
            <app-empty-list-row text="Aucune cible définie." icon="flag" />
          } @else {
          <app-list-frame>
          @for (g of history(); track g.uuid) {
            <app-list-row [selected]="g.uuid === activeGoal()?.uuid" [clickable]="false">
              <div class="goal__main">
                <span class="goal__date">
                  À partir du <span class="date-value">{{ formatDate(g.effectiveFrom) }}</span>
                  @if (g.uuid === activeGoal()?.uuid) {
                    <span class="goal__badge">active</span>
                  }
                </span>
                <span class="goal__sub">
                  <span [style.color]="macroColor.kcal">{{ round(g.kcal) }} kcal</span> ·
                  <span [style.color]="macroColor.protein">P {{ round(g.proteinG) }} g</span> ·
                  <span [style.color]="macroColor.carbs">G {{ round(g.carbsG) }} g</span> ·
                  <span [style.color]="macroColor.fat">L {{ round(g.fatG) }} g</span> ·
                  <span [style.color]="macroColor.fiber">F {{ round(fiberTarget(g.kcal)) }} g</span>
                </span>
              </div>
              <app-action-icon-button icon="more_vert" [size]="34" [iconSize]="20" (clicked)="goalForOptions.set(g)" />
            </app-list-row>
          }
          </app-list-frame>
          }
        </div>
      </div>

      <!-- ⋮ cible : modifier / supprimer. -->
      <app-options-bottom-sheet
        [open]="goalForOptions() !== null"
        [title]="'Cible du ' + formatDate(goalForOptions()?.effectiveFrom ?? '')"
        [actions]="goalActions"
        (dismissRequest)="goalForOptions.set(null)"
        (actionSelected)="onGoalOption($event)"
      />

      <!-- Création / modification d'une cible (macro-first, D12). -->
      <app-form-dialog
        [open]="formOpen()"
        [title]="editUuid() ? 'Modifier la cible' : 'Nouvelle cible'"
        confirmText="Enregistrer"
        [confirmEnabled]="formValid()"
        disabledReason="Date + 3 macros (≥ 0, au moins une > 0) requises"
        (confirm)="submitForm()"
        (dismiss)="formOpen.set(false)"
      >
        @if (formOpen()) {
          <!-- Date « Active à partir du » : champ date DS (date + icône calendrier) → ouvre le DatePicker. -->
          <span class="form-date__label">Active à partir du</span>
          <app-date-field [value]="formatDate(fFrom())" (clicked)="datePickerOpen.set(true)" />
          <!-- Macros via roues de sélection (miroir Android) : P / G / L côte à côte. -->
          <div class="form-wheels">
            <div class="form-wheel">
              <span class="form-wheel__label" [style.color]="macroColor.protein">{{ macroLabel.protein }}</span>
              <app-wheel-picker
                [min]="0"
                [max]="400"
                [selected]="fProtein()"
                (selectedChange)="fProtein.set($event)"
              />
            </div>
            <div class="form-wheel">
              <span class="form-wheel__label" [style.color]="macroColor.carbs">{{ macroLabel.carbs }}</span>
              <app-wheel-picker
                [min]="0"
                [max]="800"
                [selected]="fCarbs()"
                (selectedChange)="fCarbs.set($event)"
              />
            </div>
            <div class="form-wheel">
              <span class="form-wheel__label" [style.color]="macroColor.fat">{{ macroLabel.fat }}</span>
              <app-wheel-picker
                [min]="0"
                [max]="300"
                [selected]="fFat()"
                (selectedChange)="fFat.set($event)"
              />
            </div>
          </div>

          <!-- Sous les roues : 2 cadres recessed côte à côte — kcal objectif (gauche) · fibres (droite). -->
          <div class="derived">
            <div class="derived__cell">
              <span class="derived__num" [style.color]="macroColor.kcal">{{ round(derived().kcal) }}</span>
              <span class="derived__cell-label">kcal objectif</span>
            </div>
            <div class="derived__cell">
              <span class="derived__num" [style.color]="macroColor.fiber">{{ round(derived().fiberG) }}</span>
              <span class="derived__cell-label">g de fibres</span>
            </div>
          </div>
        }
      </app-form-dialog>

      <!-- DatePicker (calendrier thémé) pour « Active à partir du » — au-dessus du form-dialog. -->
      <app-custom-date-picker-dialog
        [open]="datePickerOpen()"
        title="Active à partir du"
        [initialIso]="fFrom()"
        (confirm)="fFrom.set($event); datePickerOpen.set(false)"
        (dismiss)="datePickerOpen.set(false)"
      />

      <app-confirmation-dialog
        [open]="goalToDelete() !== null"
        title="Supprimer la cible"
        [message]="deleteMsg()"
        confirmButtonText="Supprimer"
        dismissButtonText="Annuler"
        (confirm)="confirmDelete()"
        (dismiss)="goalToDelete.set(null)"
      />

    </section>
  `,
  styles: [
    `
      /* Le host remplit tout le viewport et « récupère » les 88px que l'outlet réserve en bas pour la
         bottom nav flottante : display:block (le host est inline par défaut) + margin-bottom négatif qui
         annule le padding-bottom:88px de l'outlet → la page fait 100dvh SANS faire scroller la fenêtre,
         et les deux lignes récupèrent ces 88px. (Le margin sur la section interne, host inline, ne
         l'annulait pas → fenêtre qui scrollait.) */
      :host {
        display: block;
        margin-bottom: -88px;
      }
      .page {
        height: 100dvh;
        display: flex;
        flex-direction: column;
        overflow: hidden;
      }
      /* Corps = grille 3 colonnes égales × 2 lignes remplissant la hauteur restante → chaque ligne = la
         moitié. 3 colonnes (et non 2) car les 2 lignes ont des proportions inversées : un panneau « large »
         (.gcell--wide) occupe 2 colonnes, un « étroit » 1 → bord interne à 1/3 (L1) ou 2/3 (L2).
         minmax(0, 1fr) (et non 1fr = minmax(auto, 1fr)) : sans ça, une ligne ne descend jamais sous le
         min-content de sa cellule → elle grandit jusqu'à la taille « naturelle » du contenu (ex. anneaux)
         et la grille déborde d'un poil → scrollbar. Avec minmax(0, …), les lignes sont strictement égales
         et le contenu (radar fill / anneaux fitHeight / tuiles) se réduit pour tenir. */
      .page__body {
        flex: 1;
        min-height: 0;
        padding: var(--page-gutter-top) var(--page-gutter) var(--space-4);
        box-sizing: border-box;
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        grid-template-rows: minmax(0, 1fr) minmax(0, 1fr);
        gap: var(--space-4);
      }
      /* Cellule de la grille (graphes) : le contenu est dimensionné pour tenir (flex / fitHeight) →
         overflow hidden coupe les arrondis sous-pixel (1px) sans afficher de scrollbar. */
      .gcell {
        min-height: 0;
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
        overflow: hidden;
      }
      /* Exception : la cellule Historique est une vraie liste → elle, peut scroller. */
      .gcell--list {
        overflow: auto;
      }
      /* Panneau « large » : occupe 2 des 3 colonnes (≈ 2/3) ; les « étroits » gardent 1 colonne (≈ 1/3). */
      .gcell--wide {
        grid-column: span 2;
      }
      /* Cible active : 2 cadres recessed empilés — valeurs en tuiles (haut, remplit la hauteur) +
         barre « Depuis le … » / bouton « Nouvelle cible » (bas, compacte). Le conteneur est transparent
         (le fond recessed est porté par chacun des 2 cadres). */
      .active {
        flex: 1;
        min-height: 0;
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
      }
      /* Cadre du bas : barre [depuis | bouton], hauteur compacte (auto). */
      .active__bar {
        display: flex;
        align-items: center;
        gap: var(--space-2);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: var(--space-2) var(--space-3);
      }
      /* « Depuis le … » / invite : prend la place à gauche, le bouton reste à droite. */
      .active__since {
        flex: 1;
        min-width: 0;
        /* Même style que « À partir du … » des rows historiques (.goal__date) : 14px, medium, gris-bleu. */
        color: var(--c-gray-blue);
        font-size: 14px;
        font-weight: var(--font-weight-medium);
      }
      /* La DATE elle-même reste en blanc (texte primaire) ; seul le label « Depuis le / À partir du » est gris-bleu. */
      .date-value {
        color: var(--app-text-primary);
      }
      /* « Depuis le » : un peu plus d'air entre le label et la date. */
      .active__since .date-value {
        margin-left: var(--space-1);
      }
      .active__empty {
        flex: 1;
        min-width: 0;
        margin: 0;
        color: var(--app-text-tertiary);
        font-size: 13px;
      }
      /* Cadre du haut : valeurs de la cible en tuiles. Recessed + remplit la hauteur dispo au-dessus
         de la barre (les tuiles s'étirent via grid-auto-rows 1fr). Calories en grand (pleine largeur)
         puis 2×2 macros. */
      .active__values {
        flex: 1;
        min-height: 0;
        display: grid;
        /* Étoile X resserrée : macros aux coins, alignées vers le centre de leur cellule (place-self). En
           réduisant les pistes du MILIEU (colonnes 1fr 1.6fr 1fr, lignes 1fr 0.45fr 1fr), les bords internes
           des cellules de coin se rapprochent du centre → les macros se resserrent un peu en diagonal
           (horizontal + vertical), kcal restant centré. Cadre inchangé (flex:1). */
        grid-template-columns: 1fr 1.6fr 1fr;
        grid-template-rows: 1fr 0.45fr 1fr;
        gap: var(--space-2);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        /* Padding canonique des cadres (app-framed-section). */
        padding: 16px;
      }
      .active__tile {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: var(--space-1);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        /* Bordure 0.5px à la couleur du macro (couleur posée par tuile en inline). */
        border: 0.5px solid transparent;
        padding: var(--space-1) var(--space-2);
        text-align: center;
      }
      /* Placement « étoile X » équilibré : kcal au centre (grande cellule), macros aux coins MAIS alignées
         vers le centre de leur cellule (place-self) → elles tombent à mi-chemin centre/coin. */
      .active__tile--kcal {
        grid-area: 2 / 2;
        /* Padding horizontal nul (la tuile remplit déjà sa cellule → contenu moins serré). */
        padding-inline: 0;
      }
      .active__tile--tl {
        grid-area: 1 / 1;
        place-self: end end;
      }
      .active__tile--tr {
        grid-area: 1 / 3;
        place-self: end start;
      }
      .active__tile--bl {
        grid-area: 3 / 1;
        place-self: start end;
      }
      .active__tile--br {
        grid-area: 3 / 3;
        place-self: start start;
      }
      /* Les 4 tuiles macro sont dimensionnées au contenu (place-self) → leur contenu colle au liseré.
         Padding horizontal plus large pour leur donner le même air L/R que la tuile Calories (qui, elle,
         remplit sa cellule). Elles restent ancrées dans leur coin (l'étoile ne bouge pas). */
      .active__tile--tl,
      .active__tile--tr,
      .active__tile--bl,
      .active__tile--br {
        padding-inline: var(--space-6);
      }
      .active__tile-num {
        font-size: 20px;
        font-weight: 600;
        font-variant-numeric: tabular-nums;
        line-height: 1.1;
      }
      .active__tile--kcal .active__tile-num {
        font-size: 26px;
      }
      .active__tile-label {
        color: var(--c-gray-blue);
        font-size: 12px;
      }

      /* 2 colonnes : analyses (gauche) + historique (droite) ; empilées sur écran étroit. */
      /* Les vues d'une section affichées CÔTE À CÔTE (plus de bascule) ; wrap empilé si trop étroit. */
      .analysis-row {
        /* Remplit la hauteur de la cellule (la ligne récupère l'espace dispo) → les cadres thirdBlue
           s'étirent jusqu'en bas, plus de trou sous eux. */
        flex: 1;
        min-height: 0;
        display: flex;
        flex-wrap: wrap;
        gap: var(--space-3);
        /* Tous les cadres d'une rangée à la MÊME hauteur (= le plus haut) → pas de trou sous les plus courts. */
        align-items: stretch;
      }
      .analysis-row__item {
        flex: 1 1 200px;
        min-width: 0;
      }
      /* Cadre thirdBlue d'un graphe du Profil macros (radar / barres) — 2 cadres côte à côte. Le fond
         recessed est porté par le cadre → il remplit toute la hauteur (align-items:stretch), donc pas
         de trou sous le graphe le plus court (ex. les barres vs le radar plus haut). */
      .profile-cell {
        display: flex;
        flex-direction: column;
        justify-content: center;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
      }
      /* Radar en mode [fill] : son host est inline par défaut → on l'étire en flex pour qu'ECharts
         ait une hauteur réelle (sinon le graphe collapse à 0 et seule la légende reste visible). */
      .profile-cell app-radar-chart {
        display: flex;
        flex: 1;
        min-height: 0;
      }
      /* Anneaux du Profil : largeur plafonnée (la hauteur suit) pour rester lisibles ; centrés dans le cadre. */
      .profile-cell app-concentric-rings-chart {
        max-width: 340px;
        align-self: center;
      }
      @media (max-width: 900px) {
        /* Mobile : pas de plein écran forcé — empilement vertical, scroll naturel de la page.
           On annule la récupération des 88px (le host reprend une hauteur de contenu normale). */
        :host {
          margin-bottom: 0;
        }
        .page {
          height: auto;
          overflow: visible;
        }
        .page__body {
          grid-template-columns: 1fr;
          grid-template-rows: auto;
        }
        /* Empilement : on annule le span 2 (sinon il force une 2ᵉ colonne implicite). */
        .gcell--wide {
          grid-column: auto;
        }
        .gcell,
        .gcell--list {
          overflow: visible;
        }
      }
      /* Répartition « Cercle » : donut (gauche) + légende part en % par macro (droite). */
      .breakdown__donut {
        display: flex;
        /* stretch (et non center) → le donut remplit la hauteur du cadre (mode [fill]) au lieu d'être
           centré sur 190px avec du vide au-dessus/dessous → plus de place verticale pour les libellés. */
        align-items: stretch;
        gap: var(--space-3);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        /* Le donut porte déjà son propre cadre recessed + padding (.dc) : on évite de doubler le padding
           horizontal ici (il rétrécissait le canvas → l'anneau et ses étiquettes manquaient de largeur). */
        padding: var(--space-2);
      }
      .breakdown__donut-chart {
        flex: 1.5;
        min-width: 0;
        /* Colonne flex étirée par le parent (align-items:stretch) → le .dc--fill du donut prend toute la
           hauteur dispo, donnant la place verticale max à la répartition des libellés. */
        display: flex;
        flex-direction: column;
        min-height: 0;
      }
      .breakdown__legend {
        flex: 1;
        min-width: 0;
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: var(--space-4);
      }
      .breakdown__legend-row {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }
      .breakdown__dot {
        width: 10px;
        height: 10px;
        border-radius: 3px;
        flex-shrink: 0;
      }
      .breakdown__legend-label {
        color: var(--app-text-secondary);
        font-size: 13px;
      }
      .breakdown__legend-pct {
        font-size: 13px;
        font-weight: 600;
        font-variant-numeric: tabular-nums;
      }
      /* « Apports au poids de corps » = 4 tuiles thirdBlue qui remplissent la colonne, SANS cadre
         (ni fond, ni padding, ni titre) : la colonne n'est qu'un conteneur flex pour la grille. */
      .breakdown__list {
        display: flex;
        flex-direction: column;
      }
      /* 2×2 tuiles « apports au poids de corps » : fond transparent + bordure gris-bleu ; les tuiles
         remplissent la hauteur dispo (grid-auto-rows 1fr) et répartissent num/label (space-evenly). */
      .bwtiles {
        flex: 1;
        display: grid;
        grid-template-columns: 1fr 1fr;
        grid-auto-rows: 1fr;
        gap: var(--space-2);
      }
      .bwtile {
        display: flex;
        flex-direction: column;
        align-items: center;
        /* space-evenly réparti verticalement + gap garanti entre num et label (un peu d'air). */
        justify-content: space-evenly;
        gap: var(--space-2);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-sm);
        padding: var(--space-3) var(--space-2);
      }
      .bwtile__top {
        display: flex;
        flex-wrap: wrap;
        align-items: baseline;
        justify-content: center;
        gap: 3px;
      }
      .bwtile__num {
        font-size: 26px;
        font-weight: 600;
        font-variant-numeric: tabular-nums;
        line-height: 1.1;
      }
      .bwtile__unit {
        color: var(--c-gray-blue);
        font-size: 12px;
      }
      .bwtile__bot {
        display: flex;
        align-items: center;
        gap: 4px;
      }
      .bwtile__label {
        color: var(--app-text-secondary);
        font-size: 15px;
        text-align: center;
      }
      .breakdown__list-hint {
        font-size: 11px;
        font-style: italic;
        color: var(--app-text-tertiary);
      }
      /* Légende « moyenne 7 j » : sous le radar, DANS le cadre recessed du Profil macros (gris-bleu). */
      .profile__caption {
        margin-top: var(--space-1);
        padding: 0 var(--space-3) var(--space-2);
        text-align: center;
        font-size: 11px;
        font-style: italic;
        color: var(--c-gray-blue);
      }

      /* Historique : cadre + filet + sélection portés par <app-list-frame> / <app-list-row> ;
         ici on ne style que le CONTENU projeté (date + macros). */
      .goal__main {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        /* Plus d'air entre « À partir du … » et la ligne des macros (miroir Android). */
        gap: var(--space-1);
      }
      .goal__date {
        color: var(--c-gray-blue);
        font-size: 14px;
        font-weight: var(--font-weight-medium);
        display: inline-flex;
        align-items: center;
        gap: var(--space-2);
      }
      .goal__badge {
        background: var(--app-primary-action);
        color: #fff;
        border-radius: var(--radius-pill);
        font-size: 11px;
        font-weight: 600;
        padding: 1px 8px;
      }
      /* Sous-ligne historique (kcal · P · G · L · F) : seuls les « · » restent en gris-bleu ; chaque valeur (kcal incluse) est colorée via son span (MACRO_COLOR). */
      .goal__sub {
        color: var(--c-gray-blue);
        font-size: 12px;
      }
      /* Label du champ date « Active à partir du » (le bouton lui-même = composant DS app-date-field). */
      .form-date__label {
        font-size: var(--font-size-caption);
        color: var(--app-text-tertiary);
      }
      /* 3 roues de macros côte à côte (miroir Android), label coloré au-dessus de chaque roue. */
      .form-wheels {
        display: flex;
        gap: var(--space-3);
      }
      .form-wheel {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        /* stretch (et non center) : avec center, l'hôte app-wheel-picker ne s'étire pas et rétrécit
           à la largeur min-content du nombre (~30px) → roues « toutes fines ». stretch la fait
           remplir la colonne (même schéma que hms-wheel-picker). */
        align-items: stretch;
        gap: var(--space-1);
      }
      .form-wheel__label {
        text-align: center;
        font-size: 13px;
        font-weight: 600;
      }
      /* Sous les roues : 2 cadres recessed (thirdBlue) côte à côte, même largeur — kcal · fibres. */
      .derived {
        display: flex;
        gap: var(--space-3);
        margin-top: var(--space-1);
      }
      .derived__cell {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 2px;
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        padding: var(--space-2) var(--space-3);
      }
      .derived__num {
        font-size: 22px;
        font-weight: 600;
        font-variant-numeric: tabular-nums;
      }
      .derived__cell-label {
        color: var(--app-text-tertiary);
        font-size: 12px;
        text-align: center;
      }
    `,
  ],
})
export class NutritionGoalsPage {
  private readonly sync = inject(SyncEngine);
  protected readonly goalRepo = inject(NutritionGoalRepository);
  private readonly mealRepo = inject(MealRepository);
  private readonly auth = inject(AuthService);

  /** Code couleur par macro (macro-colors.ts) — partagé template + donut + légende. */
  protected readonly macroColor = MACRO_COLOR;
  protected readonly macroLabel = MACRO_LABEL;
  protected readonly macroIcon = MACRO_ICON;

  protected readonly activeGoal = computed(() =>
    this.goalRepo.activeGoalFor(this.goalRepo.goals(), todayIso()),
  );

  /** Historique trié par effectiveFrom décroissant (plus récentes d'abord). */
  protected readonly history = computed(() =>
    [...this.goalRepo.goals()].sort((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom)),
  );

  // -------------------- Analyse de la cible active --------------------

  /** Répartition calorique par macro de la cible active (kcal + %). */
  protected readonly breakdown = computed(() => {
    const g = this.activeGoal();
    return g ? macroKcalBreakdown(g) : [];
  });

  /** Parts du donut (kcal par macro, couleur dédiée). */
  protected readonly donutSlices = computed<DonutSlice[]>(() =>
    this.breakdown().map((b) => ({
      // Nom complet sur les parts (donut Objectifs assez large : le composant ajuste l'anneau à la
      // largeur des libellés). Cohérent avec la légende à droite, qui affiche déjà le nom complet.
      label: MACRO_LABEL[b.key],
      value: this.round(b.kcal),
      color: MACRO_COLOR[b.key],
    })),
  );

  /** Poids du profil (kg) — null si non renseigné (base des apports g/kg). */
  protected readonly weightKg = computed(() => this.auth.currentUser()?.weightKg ?? null);
  protected readonly hasWeight = computed(() => {
    const w = this.weightKg();
    return w !== null && w > 0;
  });

  /**
   * Tuiles « apports au poids de corps », dans l'ordre de la grille 2×2 : Glucides (haut-gauche),
   * Lipides (haut-droite), Protéines (bas-gauche) en g/kg (`—` sans poids), puis densité fibres
   * (bas-droite, g/1000 kcal). `num`/`unit` séparés pour l'affichage grand chiffre + petite unité.
   */
  protected readonly bodyweightTiles = computed(() => {
    const g = this.activeGoal();
    const w = this.weightKg();
    const grams: Record<'protein' | 'carbs' | 'fat', number> = {
      protein: g?.proteinG ?? 0,
      carbs: g?.carbsG ?? 0,
      fat: g?.fatG ?? 0,
    };
    const perKg = (['carbs', 'fat', 'protein'] as const).map((key) => {
      const v = macroPerKg(grams[key], w);
      return { key, num: v !== null ? this.round1(v).toString() : '—', unit: v !== null ? 'g/kg' : '' };
    });
    return [
      ...perKg,
      { key: 'fiber' as const, num: this.round(this.fiberDensityValue()).toString(), unit: 'g/1000 kcal' },
    ];
  });

  protected readonly fiberDensityValue = computed(() => {
    const g = this.activeGoal();
    return g ? fiberDensity(g.kcal) : 0;
  });

  /** Cibles (kcal + 4 macros) de la cible active — pour le comparatif vs réel. */
  protected readonly goalTargets = computed<MacroTargets | null>(() => {
    const g = this.activeGoal();
    return g
      ? { kcal: g.kcal, protein: g.proteinG, carbs: g.carbsG, fat: g.fatG, fiber: this.fiberTarget(g.kcal) }
      : null;
  });

  /**
   * Moyenne consommée par jour sur les 7 derniers jours (agrégation journal via
   * nutrition-stats-utils) — comparée à la cible quotidienne dans le panneau de barres.
   */
  protected readonly weekAvg = computed(() => {
    const end = todayIso();
    const start = addDays(end, -6);
    const agg = aggregateNutrition(
      this.mealRepo.entries(),
      this.mealRepo.meals(),
      this.goalRepo.goals(),
      start,
      end,
      'DAILY',
    );
    const days = Math.max(1, agg.buckets.length);
    const avg = (arr: number[]) => arr.reduce((s, v) => s + v, 0) / days;
    return {
      kcal: avg(agg.consumed.kcal),
      macros: {
        protein: avg(agg.consumed.protein),
        carbs: avg(agg.consumed.carbs),
        fat: avg(agg.consumed.fat),
        fiber: avg(agg.consumed.fiber),
      } as MacroAmounts,
    };
  });

  /** Anneaux concentriques macro (Profil macros) — adaptateur partagé macroRingViews. */
  protected readonly macroRings = computed(() =>
    macroRingViews(this.weekAvg().kcal, this.weekAvg().macros, this.goalTargets()),
  );
  /** Total kcal (moyenne 7 j) au centre de la pile d'anneaux. */
  protected readonly macroRingsCenterText = computed(() => Math.round(this.weekAvg().kcal).toString());

  /**
   * Radar comparatif « cible vs réel 7 j » (composant DS radar-chart, 2 tracés superposés via
   * macroRadarData) : le réel moyen /jour des 7 derniers jours (weekAvg, même agrégation que le
   * panneau de barres) tracé en % de la cible quotidienne (rempli) + la cible en repère à 100 %
   * (trait). Légende « Réel (7 j) » / « Cible ». Vide tant qu'aucune cible n'est active (le bloc
   * d'analyse ne s'affiche alors pas).
   */
  protected readonly comparisonRadar = computed(() => {
    const targets = this.goalTargets();
    if (!targets) return { axes: [], series: [] };
    return macroRadarData(this.weekAvg().macros, targets, { value: 'Réel (7 j)', target: 'Cible' });
  });

  // -------------------- Options cible --------------------

  protected readonly goalForOptions = signal<LocalNutritionGoal | null>(null);
  protected readonly goalActions: SheetAction[] = [
    { label: 'Modifier', icon: 'edit', color: 'var(--c-blue-medium)' },
    { label: 'Redéfinir comme objectif', icon: 'flag', color: 'var(--c-medium-green)' },
    { label: 'Supprimer', icon: 'delete', color: 'var(--c-red-medium)' },
  ];

  protected onGoalOption(label: string): void {
    const goal = this.goalForOptions();
    this.goalForOptions.set(null);
    if (!goal) return;
    if (label === 'Modifier') this.openEdit(goal);
    else if (label === 'Redéfinir comme objectif') this.redefineAsGoal(goal);
    else if (label === 'Supprimer') this.goalToDelete.set(goal);
  }

  /**
   * « Redéfinir comme objectif » (options d'une cible passée) : reprend ses macros dans une nouvelle
   * cible effective aujourd'hui → elle redevient la cible active (plus grand effectiveFrom ≤ aujourd'hui).
   * Réutilise la même logique de création que « Nouvelle cible » (goalRepo.create).
   */
  protected redefineAsGoal(goal: LocalNutritionGoal): void {
    void this.goalRepo.create({
      effectiveFrom: todayIso(),
      kcal: goal.kcal,
      proteinG: goal.proteinG,
      carbsG: goal.carbsG,
      fatG: goal.fatG,
    });
  }

  // -------------------- Formulaire (création / modification) --------------------

  protected readonly formOpen = signal(false);
  protected readonly datePickerOpen = signal(false);
  protected readonly editUuid = signal<string | null>(null);
  protected readonly fFrom = signal('');
  // Macros saisies via roues (nombres entiers, miroir Android), pas via champs texte.
  protected readonly fProtein = signal(0);
  protected readonly fCarbs = signal(0);
  protected readonly fFat = signal(0);

  /** Total kcal + fibres dérivés en direct des 3 macros saisies (macro-first, D12). */
  protected readonly derived = computed(() =>
    deriveGoalFromMacros({
      proteinG: this.fProtein(),
      carbsG: this.fCarbs(),
      fatG: this.fFat(),
    }),
  );

  /** Nouvelle cible : pré-remplie depuis la cible active (ajustement incrémental fréquent). */
  protected openCreate(): void {
    const g = this.activeGoal();
    this.editUuid.set(null);
    this.fFrom.set(todayIso());
    this.fProtein.set(g ? Math.round(g.proteinG) : 0);
    this.fCarbs.set(g ? Math.round(g.carbsG) : 0);
    this.fFat.set(g ? Math.round(g.fatG) : 0);
    this.formOpen.set(true);
  }

  protected openEdit(goal: LocalNutritionGoal): void {
    this.editUuid.set(goal.uuid);
    this.fFrom.set(goal.effectiveFrom);
    this.fProtein.set(Math.round(goal.proteinG));
    this.fCarbs.set(Math.round(goal.carbsG));
    this.fFat.set(Math.round(goal.fatG));
    this.formOpen.set(true);
  }

  protected readonly formValid = computed(
    () => /^\d{4}-\d{2}-\d{2}$/.test(this.fFrom()) && this.derived().kcal > 0,
  );

  protected submitForm(): void {
    if (!this.formValid()) return;
    // kcal stockée = total dérivé des macros (D12) ; les fibres ne sont pas une colonne.
    const values = {
      effectiveFrom: this.fFrom(),
      kcal: this.derived().kcal,
      proteinG: this.fProtein(),
      carbsG: this.fCarbs(),
      fatG: this.fFat(),
    };
    this.formOpen.set(false);
    const uuid = this.editUuid();
    if (uuid) void this.goalRepo.update(uuid, values);
    else void this.goalRepo.create(values);
  }

  // -------------------- Supprimer --------------------

  protected readonly goalToDelete = signal<LocalNutritionGoal | null>(null);
  protected readonly deleteMsg = computed(() => {
    const g = this.goalToDelete();
    return g
      ? `Supprimer la cible du ${this.formatDate(g.effectiveFrom)} ? Les jours concernés retomberont sur la cible précédente.`
      : '';
  });

  protected confirmDelete(): void {
    const g = this.goalToDelete();
    this.goalToDelete.set(null);
    if (g) void this.goalRepo.remove(g.uuid);
  }

  // -------------------- Helpers d'affichage --------------------

  protected formatDate(iso: string): string {
    if (!iso) return '';
    const [y, m, d] = iso.split('-');
    return `${d}/${m}/${y}`;
  }
  protected round(v: number): number {
    return Math.round(v);
  }
  protected round1(v: number): number {
    return Math.round(v * 10) / 10;
  }

  /** Cible fibres dérivée du kcal de l'objectif (15 g/1000 kcal) — même source que le journal/stats. */
  protected fiberTarget(kcal: number): number {
    return fiberTargetG(kcal) ?? 0;
  }

  constructor() {
    void this.sync.syncAll().catch(() => undefined);
  }
}
