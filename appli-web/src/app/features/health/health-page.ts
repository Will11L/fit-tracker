import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  viewChild,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SyncEngine } from '@core/sync/sync-engine';
import { AuthService } from '@core/auth/auth.service';
import { HealthNavService } from './health-nav.service';
import { isHealthSection } from './health-sections';
import { ScreenTitleBar } from '@designsystem/common_components/screen-title-bar';
import { TitledDivider } from '@designsystem/common_components/titled-divider';
import { LabeledProgressBar } from '@designsystem/common_components/labeled-progress-bar';
import { ActionIconButton } from '@designsystem/common_components/action-icon-button';
import { HealthRepository } from './health.repository';
import { EnergyWeekChart } from './energy-week-chart';
import { HealthBarChart } from './health-bar-chart';
import { HealthLineChart } from './health-line-chart';
import { SleepHypnogramChart } from './sleep-hypnogram-chart';
import { fromActive, fromTotal, type CalorieBreakdown, type CalorieProfile } from './calorie-math';
import { MealRepository } from '../nutrition/meal.repository';
import { entryTotals } from '../nutrition/journal-utils';
import {
  activeHealthGoal,
  averageOfFilledDays,
  clipFutureSlots,
  currentSlot,
  formatHoursMinutes,
  HR_INTRADAY_TYPE,
  latestMetric,
  latestSlot,
  metricByDayCalendar,
  metricBySlot,
  SLEEP_INTRADAY_TYPE,
  sleepPhaseTimeline,
  sleepSessionsForDay,
  sleepStagesByDayCalendar,
  slotHhmm,
  stepProgress,
  stepsByDayCalendar,
  stepsBySlot,
  stepsForDay,
  weekDaysEndingToday,
} from './health-aggregations';

/** Série 7 jours prête pour le chart : valeurs alignées, quantièmes et moyenne des jours renseignés. */
interface WeekSeries {
  values: number[];
  /** Valeurs pour le line chart : jours vides = `null` (point absent, aucune interpolation). */
  lineValues: (number | null)[];
  labels: string[];
  average: number | null;
  hasData: boolean;
}

/**
 * Hub Santé web (`/health`) — **lecture seule**, inspiré du hub Android (`HealthDashboardScreen`).
 * **Rail horizontal** d'une seule rangée : 1 colonne = 1 section (Pas / FC / Sommeil / SpO2 /
 * Distance & calories / Poids / Stress, ordre = [HEALTH_SECTIONS]). ≈ 3 colonnes visibles à la fois
 * (largeur = ancienne colonne de grille), les autres s'atteignent en slidant (scroll-snap centré).
 * La barre basse (mode Santé) et le drawer (section Santé) pointent chacun une colonne : le clic la
 * centre (via [HealthNavService]), l'item actif suit le scroll manuel (bouton actif = fond couleur de
 * section). Chaque colonne est une PILE de cards thirdBlue indépendantes (miroir des SectionCard/
 * ChartFrame Android) : le titre de section coiffe la 1re card, puis 1 card par bloc (chiffres clés /
 * intraday / tendance 7 j — Pas & FC & Poids en courbe lissée, les autres en barres, moyenne pointillée).
 * SpO2 a son cadre propre (sortie de Sommeil). Les données viennent du serveur
 * via Dexie (offline-first) ; aucune écriture (pas d'accès Health Connect). Objectif de pas en lecture
 * seule (édition sur Android), pesée saisie sur Android. Reste lisible en colonne étroite sur navigateur
 * mobile (charts compacts) où le slide devient le mode de navigation principal.
 */
@Component({
  selector: 'app-health-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ScreenTitleBar,
    TitledDivider,
    LabeledProgressBar,
    ActionIconButton,
    EnergyWeekChart,
    HealthBarChart,
    HealthLineChart,
    SleepHypnogramChart,
  ],
  template: `
    <section class="page">
      <div class="page__header">
        <app-screen-title-bar title="Aperçu santé" />
        <app-action-icon-button
          class="page__refresh"
          icon="refresh"
          [hasBackground]="false"
          (clicked)="refresh()"
        />
      </div>

      <!-- Rail horizontal : un domaine par colonne, une seule rangée. ≈ 3 colonnes visibles (largeur =
           ancienne colonne de grille) ; les autres s'atteignent en slidant (scroll-snap centré). Ordre
           = HEALTH_SECTIONS (data-slug = ancre stable pour le centrage barre basse / drawer / deep-link).
           L'intraday reste dans la colonne Pas en largeur réduite (barres fines, assumé dense). -->
      <div class="rail" #rail>
        <!-- Pas : pile de cards thirdBlue (compteur+objectif / intraday 30 min / 7 j) -->
        <div class="col" data-slug="pas">
          <div class="card">
            <app-titled-divider title="Pas" [color]="'var(--c-light-green)'" />
            <!-- Ligne unique « X / objectif » à gauche + barre de progression (avec %) à droite. -->
            <div class="metric steps-line">
              <span class="metric__value">{{ steps().total }}</span>
              <span class="metric__sub">
                {{ goalTarget() !== null ? '/ ' + goalTarget() : 'aucun objectif' }}
              </span>
              @if (goalTarget() !== null) {
                <app-labeled-progress-bar
                  class="steps-line__bar"
                  [progress]="steps().progress"
                  [troughColor]="'var(--c-second-blue)'"
                />
              }
            </div>
          </div>
          <!-- Intraday TOUJOURS rendu (barres plates à zéro si pas de données du jour, miroir Android) :
               le chart ne doit jamais disparaître faute de données. -->
          <div class="card">
            <div class="chart">
              <app-titled-divider title="Aujourd'hui — par 30 min" [color]="'var(--c-light-green)'" [size]="12" />
              <app-health-bar-chart
                [values]="steps().intraday"
                mode="INTRADAY"
                color="var(--c-light-green)"
                valueSuffix=" pas"
                seriesName="Pas"
              />
            </div>
          </div>
          @if (steps().week.hasData) {
            <div class="card">
              <div class="chart">
                <app-titled-divider title="7 derniers jours" [color]="'var(--c-light-green)'" [size]="12" />
                <app-health-line-chart
                  [values]="steps().week.lineValues"
                  [labels]="steps().week.labels"
                  [average]="steps().week.average"
                  color="var(--c-light-green)"
                  valueSuffix=" pas"
                  seriesName="Pas"
                />
              </div>
            </div>
          }
        </div>

        <!-- Fréquence cardiaque : card résumé bpm / card intraday / card 7 j (plaquée en bas) -->
        <div class="col" data-slug="fc">
          <div class="card">
            <app-titled-divider title="Fréquence cardiaque" [color]="'var(--c-orange-medium)'" />
            @if (cardio().avgBpm !== null || cardio().intradayHasData || cardio().week.hasData) {
              @if (cardio().avgBpm !== null || cardio().intradayHasData) {
                <!-- Ligne « X bpm · moy. Y bpm · heure » (space-evenly) : dernière tranche en hero
                     textPrimary, moyenne secondaire, heure dans un chip arrondi (bgSurface) =
                     MeasureTimePill. Tokens omis proprement s'ils manquent. -->
                <div class="hr-line">
                  @if (cardio().lastBpm !== null) {
                    <span class="hr-line__last">{{ cardio().lastBpm }} bpm</span>
                  }
                  @if (cardio().avgBpm !== null) {
                    <span class="hr-line__avg">moy. {{ cardio().avgBpm }} bpm</span>
                  }
                  @if (cardio().lastTime !== null) {
                    <span class="hr-line__time">{{ cardio().lastTime }}</span>
                  }
                </div>
              } @else {
                <!-- Jour fraîchement entamé : rien encore mesuré aujourd'hui. -->
                <p class="empty">Pas encore de données.</p>
              }
            } @else {
              <p class="empty">Aucune donnée de fréquence cardiaque.</p>
            }
          </div>
          <!-- Intraday TOUJOURS rendu (barres plates si rien encore aujourd'hui, comme les Pas) :
               le cadre sert d'aperçu plutôt que de laisser un vide. -->
          <div class="card">
            <div class="chart">
              <app-titled-divider title="Aujourd'hui" [color]="'var(--c-orange-medium)'" [size]="12" />
              <app-health-bar-chart
                [values]="cardio().intraday"
                mode="INTRADAY"
                color="var(--c-orange-medium)"
                valueSuffix=" bpm"
                seriesName="Fréquence cardiaque"
              />
            </div>
          </div>
          @if (cardio().week.hasData) {
            <!-- 7 j : rangées partagées entre colonnes (subgrid) → la card tombe dans la même rangée
                 que le 7 j des autres colonnes, alignée automatiquement (plus de « plaqué en bas »). -->
            <div class="card">
              <div class="chart">
                <app-titled-divider title="7 derniers jours" [color]="'var(--c-orange-medium)'" [size]="12" />
                <app-health-line-chart
                  [values]="cardio().week.lineValues"
                  [labels]="cardio().week.labels"
                  [average]="cardio().week.average"
                  color="var(--c-orange-medium)"
                  valueSuffix=" bpm"
                  seriesName="Fréquence cardiaque"
                />
              </div>
            </div>
          }
        </div>

        <!-- Sommeil : card dormi+sessions / card intraday / card 7 j empilé+légende -->
        <div class="col" data-slug="sommeil">
          <div class="card">
            <app-titled-divider title="Sommeil" [color]="'var(--app-primary-action)'" />
            @if (sleep().latestMin !== null || sleep().intradayHasData || sleep().week.hasData) {
              @if (sleep().latestMin !== null) {
                <!-- Tout sur UNE ligne façon FC : durée dormie en hero (blanc) + heures de la
                     nuit par session (mise au lit / endormissement), space-evenly bords compris. -->
                <div class="hr-line">
                  <span class="hr-line__last">{{ formatMinutes(sleep().latestMin!) }}</span>
                  @for (s of sleep().sessions; track s.bedTime) {
                    <span class="sleep-times__label">Au lit à <span class="sleep-times__value">{{ s.bedTime }}</span></span>
                    <span class="sleep-times__label">Endormi à <span class="sleep-times__value">{{ s.asleepTime }}</span></span>
                  }
                </div>
              }
            } @else {
              <p class="empty">Aucune donnée de sommeil.</p>
            }
          </div>
          <!-- Hypnogramme « Cette nuit » (phases exactes, éveillé en haut → profond en bas) ;
               fallback barres intraday 30 min tant que les slices ne sont pas synchronisées. -->
          @if (sleep().phases.length > 0) {
            <div class="card">
              <div class="chart">
                <app-titled-divider title="Cette nuit" [color]="'var(--app-primary-action)'" [size]="12" />
                <app-sleep-hypnogram-chart
                  [points]="sleep().phases"
                  [phaseColors]="sleepStageColors"
                />
                <div class="legend">
                  @for (label of sleepStageLabels; track label; let i = $index) {
                    <span class="legend__item">
                      <span class="legend__dot" [style.background]="sleepStageColors[i]"></span>{{ label }}
                    </span>
                  }
                </div>
              </div>
            </div>
          } @else {
            <!-- Fallback barres intraday TOUJOURS rendu (barres plates = aperçu, comme les Pas). -->
            <div class="card">
              <div class="chart">
                <app-titled-divider title="Aujourd'hui — par 30 min" [color]="'var(--app-primary-action)'" [size]="12" />
                <app-health-bar-chart
                  [values]="sleep().intraday"
                  mode="INTRADAY"
                  color="var(--app-primary-action)"
                  valueSuffix=" min"
                  seriesName="Sommeil"
                />
              </div>
            </div>
          }
          @if (sleep().week.hasData || sleep().stages.hasData) {
            <div class="card">
              <div class="chart">
                <app-titled-divider title="7 derniers jours" [color]="'var(--app-primary-action)'" [size]="12" />
                @if (sleep().stages.hasData) {
                  <!-- Barres EMPILÉES par phase (profond/léger/paradoxal/éveillé) + légende,
                       parité SleepStagesWeekFrame Android. -->
                  <app-health-bar-chart
                    [values]="sleep().week.values"
                    [labels]="sleep().week.labels"
                    [average]="sleep().stagesAverage"
                    valueSuffix=" min"
                    fill
                    [stackedValues]="sleep().stages.stacked"
                    [stackColors]="sleepStageColors"
                    [stackLabels]="sleepStageLabels"
                  />
                  <div class="legend">
                    @for (label of sleepStageLabels; track label; let i = $index) {
                      <span class="legend__item">
                        <span class="legend__dot" [style.background]="sleepStageColors[i]"></span>{{ label }}
                      </span>
                    }
                  </div>
                } @else {
                  <app-health-bar-chart
                    [values]="sleep().week.values"
                    [labels]="sleep().week.labels"
                    [average]="sleep().week.average"
                    color="var(--app-primary-action)"
                    valueSuffix=" min"
                    seriesName="Sommeil"
                    fill
                  />
                }
              </div>
            </div>
          }
        </div>

        <!-- SpO2 : oxygène du sang (mesure nocturne). Cadre propre (sorti de Sommeil) : card % / card
             courbe du jour / card 7 j (health-bar-chart, comme le hub Android). -->
        <div class="col" data-slug="spo2">
          <div class="card">
            <app-titled-divider title="SpO2 — oxygène du sang" [color]="'var(--c-light-blue)'" />
            @if (spo2().latest !== null || spo2().intradayHasData || spo2().week.hasData) {
              @if (spo2().latest !== null) {
                <!-- Ligne façon FC (space-evenly) : dernière saturation en hero · moyenne
                     (mesures du jour, sinon 7 j) · heure/date de la mesure en chip. -->
                <div class="hr-line">
                  <span class="hr-line__last">{{ spo2().latest }} %</span>
                  @if (spo2().avg !== null) {
                    <span class="hr-line__avg">moy. {{ spo2().avg }} %</span>
                  }
                  @if (spo2().lastTime !== null) {
                    <span class="hr-line__time">{{ spo2().lastTime }}</span>
                  }
                </div>
              }
            } @else {
              <p class="empty">Aucune donnée de SpO2.</p>
            }
          </div>
          <!-- O2 du jour en COURBE (1 point par mesure, gaps jamais interpolés) — TOUJOURS
               rendue (grille vide = aperçu, comme les Pas, plutôt qu'un vide). -->
          <div class="card">
            <div class="chart">
              <app-titled-divider title="Aujourd'hui" [color]="'var(--c-light-blue)'" [size]="12" />
              <app-health-line-chart
                [values]="spo2().intraday"
                mode="INTRADAY"
                color="var(--c-light-blue)"
                valueSuffix=" %"
                seriesName="SpO2"
              />
            </div>
          </div>
          @if (spo2().week.hasData) {
            <div class="card">
              <div class="chart">
                <app-titled-divider title="7 derniers jours" [color]="'var(--c-light-blue)'" [size]="12" />
                <app-health-bar-chart
                  [values]="spo2().week.values"
                  [labels]="spo2().week.labels"
                  [average]="spo2().week.average"
                  color="var(--c-light-blue)"
                  valueSuffix=" %"
                  seriesName="SpO2"
                  fill
                />
              </div>
            </div>
          }
        </div>

        <!-- Distance & calories : card chiffres clés (distance + total calories, 1 seule ligne) / card
             détail calories (Métabolisme/Activité/Total + source) / card tendance 7 j → 3 rangées comme
             les autres colonnes. Avant : 1 grosse card de ~5 lignes qui imposait sa hauteur à TOUTES les
             1res cards (subgrid) → rangée 1 trop haute partout. -->
        <div class="col" data-slug="energie">
          <div class="card">
            <app-titled-divider title="Distance & calories" [color]="'var(--c-turquoise)'" />
            @if (energyHasData()) {
              <!-- Chiffres clés sur UNE ligne façon FC (space-evenly, bords compris) : distance ·
                   total calories (turquoise). L'un manquant → on montre celui qui existe. -->
              <div class="hr-line">
                @if (distance().latest !== null) {
                  <span class="hr-line__group">
                    <span class="metric__value" [style.color]="'var(--c-turquoise)'">{{ round1(distance().latest!) }}</span>
                    <span class="metric__sub">{{ distanceUnit() }}</span>
                  </span>
                }
                @if (energy().breakdown?.totalKcal != null) {
                  <span class="hr-line__group">
                    <span class="metric__value" [style.color]="'var(--c-turquoise)'">{{ energy().breakdown!.totalKcal }}</span>
                    <span class="metric__sub">kcal</span>
                  </span>
                }
              </div>
            } @else {
              <p class="empty">Aucune donnée (enregistrée seulement pendant une séance).</p>
            }
          </div>
          <!-- Détail calories : Métabolisme (est.) / Activité / Total + part d'activité (barre) +
               balance énergétique du jour (consommé journal vs dépensé — v1 de la tâche Notion
               « Santé × Nutrition », la tendance 7 j reste au backlog) + provenance de la donnée. -->
          @if (energy().breakdown !== null) {
            <div class="card">
              <div class="chart">
                <app-titled-divider title="Détail calories" [color]="'var(--c-turquoise)'" [size]="12" />
                <div class="metric metric--wrap">
                  @if (energy().breakdown; as b) {
                    @if (b.bmrKcal !== null) {
                      <span class="subline">Métabolisme (est.) · <strong>{{ b.bmrKcal }} kcal</strong></span>
                    }
                    @if (b.activeKcal !== null) {
                      <span class="subline">Activité · <strong [style.color]="'var(--c-orange-medium)'">{{ b.activeKcal }} kcal</strong></span>
                    }
                    @if (b.totalKcal !== null) {
                      <span class="subline">Total · <strong [style.color]="'var(--c-turquoise)'">{{ b.totalKcal }} kcal</strong></span>
                    }
                    @if (activityPct(b); as pct) {
                      <!-- Part de l'activité dans la dépense : trough neutre + remplissage orange. -->
                      <span class="split-bar"><span class="split-bar__fill" [style.width.%]="pct"></span></span>
                      <span class="subline">Activité · <strong [style.color]="'var(--c-orange-medium)'">{{ pct }} %</strong> de la dépense</span>
                    }
                  }
                  @if (energyBalance(); as bal) {
                    <span class="subline">Consommé (journal) · <strong>{{ bal.consumed }} kcal</strong></span>
                    <span class="subline">Balance ·
                      <strong [style.color]="bal.delta <= 0 ? 'var(--c-medium-green)' : 'var(--c-orange-medium)'">
                        {{ bal.delta > 0 ? '+' : '' }}{{ bal.delta }} kcal · {{ bal.delta <= 0 ? 'déficit' : 'surplus' }}
                      </strong>
                    </span>
                  }
                  @if (energy().source !== 'NONE') {
                    <span class="source">{{ energy().source === 'WATCH' ? 'Source : Montre' : 'Source : Health Connect' }}</span>
                  }
                </div>
              </div>
            </div>
          }
          <!-- Tendance 7 j COMBINÉE (double axe masqué) : barres = activité (kcal,
               turquoise identité de section) + courbe = distance (trait clair neutre),
               légende sous le chart. Chaque série s'omet proprement si vide. -->
          @if (energy().week.hasData || distance().week.hasData) {
            <div class="card">
              <div class="chart">
                <app-titled-divider title="7 derniers jours" [color]="'var(--c-turquoise)'" [size]="12" />
                <app-energy-week-chart
                  [labels]="energy().week.labels"
                  [kcal]="energy().week.values"
                  [kcalAverage]="energy().week.average"
                  [distance]="distance().week.lineValues"
                  [distanceUnit]="distanceUnit()"
                  fill
                />
                <div class="legend">
                  <span class="legend__item">
                    <span class="legend__dot" [style.background]="'var(--c-turquoise)'"></span>Activité (kcal)
                  </span>
                  <span class="legend__item">
                    <span class="legend__dot" [style.background]="'var(--app-text-primary)'"></span>Distance
                  </span>
                </div>
              </div>
            </div>
          }
        </div>

        <!-- Poids : pesées manuelles synchronisées (WEIGHT_KG, saisie côté Android — web lecture
             seule). card valeur (+ pastille de date si pas d'aujourd'hui) / card 7 j / card 30 j en
             courbe jaune (jours sans pesée = gap, jamais interpolé — parité WeightSection Android). -->
        <div class="col" data-slug="poids">
          <div class="card">
            <app-titled-divider title="Poids" [color]="'var(--c-yellow-medium)'" />
            @if (weight().latest !== null) {
              <!-- Ligne façon FC (space-evenly) : dernière pesée en hero · moyenne des
                   pesées des 7 derniers jours · date de la pesée en chip. -->
              <div class="hr-line">
                <span class="hr-line__last">{{ weight().latest }} kg</span>
                @if (weight().avg !== null) {
                  <span class="hr-line__avg">moy. {{ weight().avg }} kg</span>
                }
                @if (weight().dateLabel !== null) {
                  <span class="hr-line__time">{{ weight().dateLabel }}</span>
                }
              </div>
            } @else {
              <p class="empty">Aucune pesée.</p>
            }
          </div>
          @if (weight().latest !== null && weight().week.hasData) {
            <div class="card">
              <div class="chart">
                <app-titled-divider title="7 derniers jours" [color]="'var(--c-yellow-medium)'" [size]="12" />
                <app-health-line-chart
                  [values]="weight().week.lineValues"
                  [labels]="weight().week.labels"
                  color="var(--c-yellow-medium)"
                  [emptySlotColor]="'var(--c-red-medium)'"
                  valueSuffix=" kg"
                  seriesName="Poids"
                />
              </div>
            </div>
          }
          @if (weight().latest !== null && weight().month.hasData) {
            <div class="card">
              <div class="chart">
                <app-titled-divider title="30 derniers jours" [color]="'var(--c-yellow-medium)'" [size]="12" />
                <app-health-line-chart
                  [values]="weight().month.lineValues"
                  [labels]="weight().month.labels"
                  color="var(--c-yellow-medium)"
                  [emptySlotColor]="'var(--c-red-medium)'"
                  valueSuffix=" kg"
                  seriesName="Poids"
                />
              </div>
            </div>
          }
        </div>

        <!-- Stress : saisie manuelle 1..5 synchronisée depuis Android (Samsung n'expose pas
             le stress dans HC) — web LECTURE SEULE : niveau du jour (hero « X/5 » · libellé ·
             date en chip, façon FC) + tendances 7 j / 30 j en courbe violette (points rouges
             des jours manquants, visuels). -->
        <div class="col" data-slug="stress">
          <div class="card">
            <app-titled-divider title="Stress" [color]="'var(--c-bright-purple)'" />
            @if (stress().latest !== null) {
              <!-- Score et libellé à la couleur de la CATÉGORIE (vert → rouge, modèle Samsung). -->
              <div class="hr-line">
                <!-- Score à la couleur de catégorie, « /100 » neutre (blanc). -->
                <span class="hr-line__last"><span [style.color]="stress().color">{{ stress().latest }}</span>/100</span>
                <span class="hr-line__avg" [style.color]="stress().color">{{ stress().label }}</span>
                @if (stress().dateLabel !== null) {
                  <span class="hr-line__time">{{ stress().dateLabel }}</span>
                }
              </div>
            } @else {
              <p class="empty">Aucune saisie (score 0–100, à saisir sur Android).</p>
            }
          </div>
          @if (stress().week.hasData) {
            <div class="card">
              <div class="chart">
                <app-titled-divider title="7 derniers jours" [color]="'var(--c-bright-purple)'" [size]="12" />
                <app-health-line-chart
                  [values]="stress().week.lineValues"
                  [labels]="stress().week.labels"
                  color="var(--c-bright-purple)"
                  [emptySlotColor]="'var(--c-red-medium)'"
                  [pointColors]="stress().weekPointColors"
                  valueSuffix="/100"
                  seriesName="Stress"
                />
              </div>
            </div>
          }
          @if (stress().month.hasData) {
            <div class="card">
              <div class="chart">
                <app-titled-divider title="30 derniers jours" [color]="'var(--c-bright-purple)'" [size]="12" />
                <app-health-line-chart
                  [values]="stress().month.lineValues"
                  [labels]="stress().month.labels"
                  color="var(--c-bright-purple)"
                  [emptySlotColor]="'var(--c-red-medium)'"
                  [pointColors]="stress().monthPointColors"
                  valueSuffix="/100"
                  seriesName="Stress"
                />
              </div>
            </div>
          }
        </div>
      </div>
    </section>
  `,
  styles: [
    `
      .page {
        display: flex;
        flex-direction: column;
      }
      /* En-tête : barre de titre pleine largeur + bouton refresh superposé à droite. */
      .page__header {
        position: relative;
      }
      .page__refresh {
        position: absolute;
        top: 50%;
        right: var(--space-3);
        transform: translateY(-50%);
      }
      /* Rail horizontal : GRILLE de colonnes (grid-auto-flow: column), 1 colonne = 1 section, défilement
         horizontal au slide/scroll avec accroche centrée (scroll-snap). ≈ 3 colonnes visibles à la
         largeur actuelle (grid-auto-columns = ancienne colonne de grille : (largeur utile − 2 gaps)/3).
         grid-template-rows: repeat(3, auto) = 3 rangées de cards PARTAGÉES entre toutes les colonnes
         (subgrid côté .col) → cards alignées en hauteur rangée par rangée (1res cards à la même hauteur,
         puis intraday, puis 7 j). overflow-x seul + hauteur naturelle → le scroll VERTICAL de la page
         (.outlet) n'est jamais capté ici ; overscroll-behavior-x: contain → le slide horizontal ne
         remonte pas au parent en bout de course. */
      .rail {
        padding: var(--page-gutter-top) var(--page-gutter) var(--space-6);
        display: grid;
        grid-auto-flow: column;
        grid-auto-columns: calc((100% - 2 * var(--space-3)) / 3);
        grid-template-rows: repeat(3, auto);
        gap: var(--space-3);
        overflow-x: auto;
        scroll-snap-type: x mandatory;
        overscroll-behavior-x: contain;
      }
      /* Colonne = section : conteneur TRANSPARENT (pas de cadre), placé sur une colonne de la grille du
         rail et étiré sur les 3 rangées (grid-row: 1 / -1). subgrid = ses cards adoptent les rangées
         PARTAGÉES du rail → hauteurs alignées entre colonnes. Les cards se placent dans l'ordre (1re
         card = rangée 1, etc.) ; une colonne à moins de cards laisse les rangées du BAS vides (cards
         restantes en haut). Accroche centrée + min-width: 0 (anti-débordement des charts) conservés. */
      .col {
        grid-row: 1 / -1;
        display: grid;
        grid-template-rows: subgrid;
        scroll-snap-align: center;
        min-width: 0;
      }
      /* Card thirdBlue (miroir SectionCard/ChartFrame Android) : recessed, coins arrondis, padding
         compact. Étirée à la hauteur de sa rangée partagée (align-self: stretch par défaut) → même
         hauteur que les cards homologues des autres colonnes. Le titre de section (divider coloré) coiffe
         la 1re card ; chaque chart porte son mini titled-divider teinté à la couleur de la section. */
      .card {
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        background: var(--app-bg-recessed);
        border-radius: var(--radius-md);
        /* Padding canonique des cadres (app-framed-section). */
        padding: 16px;
      }
      /* Bloc chart dans une card : mini titled-divider teinté (label) + chart. */
      /* Bloc chart d'une card : absorbe la hauteur restante (rangées égalisées par
         subgrid) — un chart en mode [fill] (barres 7 j) remplit cet espace, les
         charts à ratio (intraday, courbes) gardent leur hauteur naturelle. */
      .chart {
        display: flex;
        flex-direction: column;
        /* Espace UNIFORME titre → chart → légende (= gap des cards). */
        gap: var(--space-2);
        flex: 1;
        min-height: 0;
      }
      .chart > app-health-bar-chart,
      .chart > app-energy-week-chart {
        flex: 1;
        min-height: 110px;
      }
      /* Heures de la nuit : « Au lit à X · Endormi à Y » (labels secondaires, heures en
         avant — miroir SleepTimeLabel Android), items de la hr-line du 1er cadre Sommeil. */
      .sleep-times__label {
        color: var(--app-text-secondary);
        font-size: 12px;
        /* Respiration entre le libellé (« Au lit à ») et son heure. */
        display: inline-flex;
        align-items: baseline;
        gap: 8px;
      }
      .sleep-times__value {
        color: var(--app-text-primary);
        font-size: 13px;
        font-weight: 600;
      }
      /* Légende des phases de sommeil (points colorés, sous les charts sommeil) :
         répartie en space-evenly sur la largeur (parité Android). */
      .legend {
        display: flex;
        flex-wrap: wrap;
        justify-content: space-evenly;
        gap: var(--space-1) var(--space-2);
      }
      .legend__item {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        color: var(--app-text-secondary);
        font-size: 11px;
      }
      .legend__dot {
        width: 8px;
        height: 8px;
        border-radius: 50%;
      }
      /* Ligne mesure : chiffre clé + libellé secondaire (compacte pour tenir en demi-largeur). */
      .metric {
        display: flex;
        align-items: baseline;
        flex-wrap: wrap;
        gap: var(--space-1) var(--space-2);
      }
      /* Ligne compteur Pas : « X / objectif » à gauche, barre (+ %) flexible à droite,
         jamais de retour à la ligne. */
      .steps-line {
        flex-wrap: nowrap;
        align-items: center;
      }
      .steps-line__bar {
        flex: 1;
        min-width: 0;
      }
      .metric--wrap {
        flex-direction: column;
        align-items: flex-start;
        gap: 2px;
      }
      .metric__value {
        font-size: 22px;
        font-weight: 600;
        font-variant-numeric: tabular-nums;
        color: var(--app-text-primary);
        line-height: 1.1;
      }
      .metric__label,
      .metric__sub {
        color: var(--app-text-secondary);
        font-size: 13px;
      }
      /* Ligne FC « X bpm · moy. Y bpm · heure » répartie en space-evenly (moy. au centre),
         alignée verticalement au centre comme Android (Row CenterVertically). padding-inline : garantit
         l'espace aux deux bords même en quadrant étroit (sinon space-evenly s'effondre à 0 → la ligne
         colle au padding du cadre) → la ligne respire dans le cadre comme les autres contenus. */
      .hr-line {
        display: flex;
        align-items: center;
        justify-content: space-evenly;
        gap: var(--space-2);
        /* Déborde du padding horizontal de la card (miroir bleedCardPadding Android) :
           space-evenly rend des écarts visuellement égaux, bords du cadre compris. */
        margin-inline: calc(-1 * var(--space-3));
      }
      /* Groupe insécable valeur+unité dans une hr-line (space-evenly sépare les enfants directs). */
      .hr-line__group {
        display: inline-flex;
        align-items: baseline;
        gap: 4px;
      }
      /* Dernière tranche = hero (textPrimary, gros/gras — miroir titleLarge Android « X bpm »). */
      .hr-line__last {
        font-size: 22px;
        font-weight: 600;
        font-variant-numeric: tabular-nums;
        color: var(--app-text-primary);
        line-height: 1.1;
      }
      .hr-line__avg {
        color: var(--app-text-secondary);
        font-size: 14px;
      }
      /* Heure = chip arrondi (bgSurface, secondaire, petit) = MeasureTimePill Android. */
      .hr-line__time {
        color: var(--app-text-secondary);
        font-size: 12px;
        background: var(--app-bg-surface);
        border-radius: var(--radius-pill);
        padding: 3px 8px;
      }
      .subline {
        color: var(--app-text-secondary);
        font-size: 13px;
      }
      .subline strong {
        color: var(--app-text-primary);
        font-weight: 600;
      }
      /* Part d'activité dans la dépense : trough neutre (secondBlue, comme les progress bars)
         + remplissage orange (couleur Activité). */
      .split-bar {
        display: block;
        width: 100%;
        height: 7px;
        border-radius: 2px;
        overflow: hidden;
        background: var(--c-second-blue);
        margin: 4px 0 2px;
      }
      .split-bar__fill {
        display: block;
        height: 100%;
        border-radius: 2px;
        background: var(--c-orange-medium);
      }
      /* Ligne de provenance de la donnée (montre / Health Connect) — discrète. */
      .source {
        color: var(--app-text-tertiary);
        font-size: 12px;
      }
      .empty {
        margin: 0;
        color: var(--app-text-tertiary);
        font-size: 13px;
      }
    `,
  ],
})
export class HealthPage {
  private readonly sync = inject(SyncEngine);
  private readonly repo = inject(HealthRepository);
  private readonly auth = inject(AuthService);
  /** Journal nutrition (Dexie) — kcal consommées du jour pour la balance énergétique. */
  private readonly mealRepo = inject(MealRepository);
  private readonly healthNav = inject(HealthNavService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  /** Conteneur du rail horizontal (source du centrage + du suivi de la colonne active au scroll). */
  private readonly railEl = viewChild<ElementRef<HTMLElement>>('rail');
  /** Anti-rafale : une seule mise à jour de l'actif par frame pendant le scroll. */
  private scrollRaf = false;

  private readonly today = this.todayIso();
  private readonly weekDays = weekDaysEndingToday(this.today, 7);
  /** Quantième de chaque jour de la semaine (labels sous les barres du chart 7 j). */
  private readonly weekLabels = this.weekDays.map((d) => String(Number(d.split('-')[2])));
  /** Fenêtre 30 jours du chart de tendance du poids. */
  private readonly monthDays = weekDaysEndingToday(this.today, 30);

  // -------------------- Pas --------------------
  protected readonly goalTarget = computed(() => {
    const g = activeHealthGoal(this.repo.goals(), this.today, 'STEPS');
    return g ? Math.round(g.target) : null;
  });
  protected readonly steps = computed(() => {
    const buckets = this.repo.stepCounts();
    const total = stepsForDay(buckets, this.today);
    // Intraday 30 min, tranches futures clippées à 0 (miroir Android — artefact proration Samsung).
    // `currentSlot(new Date())` = heure LOCALE du navigateur → aligné aux buckets "HH:MM" locaux
    // (ex. à 12:30, slots 0..25 conservés = 00:00→12:30 ; seuls 13:00+ masqués). Jamais de sur-clip.
    const intraday = clipFutureSlots(stepsBySlot(buckets, this.today), currentSlot(new Date()));
    return {
      total,
      progress: stepProgress(total, this.goalTarget()),
      intraday,
      week: this.weekSeries(stepsByDayCalendar(buckets, this.weekDays)),
    };
  });

  // -------------------- Fréquence cardiaque --------------------
  // Deux métriques distinctes coexistent : HEART_RATE = moyenne 24 h du jour (row start_time null,
  // poussée par Android) → « moy. Y bpm » + chart 7 j ; HEART_RATE_INTRADAY = tranches 30 min du jour
  // (rows start_time posé) → chart « Aujourd'hui » + dernière tranche (« X bpm » + heure). Le filtre par
  // type exact garantit que les tranches ne polluent jamais la moyenne ni le 7 j.
  protected readonly cardio = computed(() => {
    const metrics = this.repo.metrics();
    // Moyenne du JOUR uniquement (parité Android hrTodayBpm) : au passage de minuit, la
    // moyenne d'hier ne s'affiche plus sous « Aujourd'hui » (→ placeholder à la place).
    const todayAvg = metrics.find((m) => m.type === 'HEART_RATE' && m.date === this.today);
    const nowSlot = currentSlot(new Date());
    const intraday = clipFutureSlots(metricBySlot(metrics, HR_INTRADAY_TYPE, this.today), nowSlot);
    const last = latestSlot(intraday, nowSlot);
    return {
      avgBpm: todayAvg ? Math.round(todayAvg.value) : null,
      intraday,
      intradayHasData: intraday.some((v) => v > 0),
      lastBpm: last ? Math.round(last.value) : null,
      lastTime: last ? slotHhmm(last.slot) : null,
      week: this.weekSeries(metricByDayCalendar(metrics, 'HEART_RATE', this.weekDays)),
    };
  });

  // -------------------- Sommeil + SpO2 --------------------
  protected readonly sleep = computed(() => {
    const metrics = this.repo.metrics();
    const latest = latestMetric(metrics, 'SLEEP');
    // Intraday sommeil (SLEEP_INTRADAY, minutes/30 min) : pas de clip futur (la nuit est
    // passée par construction, parité Android qui ne clippe que les pas proratés).
    const intraday = metricBySlot(metrics, SLEEP_INTRADAY_TYPE, this.today);
    const stages = sleepStagesByDayCalendar(metrics, this.weekDays);
    return {
      latestMin: latest ? Math.round(latest.value) : null,
      sessions: sleepSessionsForDay(metrics, this.today),
      // Hypnogramme « Cette nuit » (slices SLEEP_SLICE_*) ; vide → fallback barres.
      phases: sleepPhaseTimeline(metrics, this.today),
      intraday,
      intradayHasData: intraday.some((v) => v > 0),
      week: this.weekSeries(metricByDayCalendar(metrics, 'SLEEP', this.weekDays)),
      stages,
      // Moyenne du chart empilé = totaux empilés (éveil compris), parité Android
      // (SleepStagesWeekFrame moyenne les sommes de segments, pas les totaux SLEEP).
      stagesAverage: averageOfFilledDays(stages.stacked.map((row) => row.reduce((a, b) => a + b, 0))),
    };
  });

  // Couleurs + libellés des 4 phases (ordre SLEEP_STAGE_TYPES) — parité sleepStageColors Android :
  // profond bleu primaire / léger gris-bleu clair / paradoxal vert / éveillé orange.
  protected readonly sleepStageColors = [
    'var(--app-primary-action)',
    'var(--c-light-gray-blue)',
    'var(--c-light-green)',
    'var(--c-orange-medium)',
  ];
  protected readonly sleepStageLabels = ['Profond', 'Léger', 'Paradoxal', 'Éveillé'];
  // SpO2 : cadre propre (sorti de Sommeil). Dernière saturation + courbe du jour (chaque mesure
  // SPO2 est synchronisée avec son start_time → 1 point par tranche mesurée, jamais interpolé) +
  // tendance 7 j (valeur la plus tardive par jour via metricByDayCalendar, ≠ somme — une
  // saturation ne s'additionne pas).
  protected readonly spo2 = computed(() => {
    const metrics = this.repo.metrics();
    const latest = latestMetric(metrics, 'SPO2');
    const intradayRaw = metricBySlot(metrics, 'SPO2', this.today);
    const last = latestSlot(intradayRaw, currentSlot(new Date()));
    const week = this.weekSeries(metricByDayCalendar(metrics, 'SPO2', this.weekDays));
    // Ligne façon FC : moy. = mesures DU JOUR si présentes, sinon moyenne 7 j ;
    // chip = heure de la dernière mesure du jour, sinon date de la dernière connue.
    const dayAvg = averageOfFilledDays(intradayRaw);
    const avg = dayAvg ?? week.average;
    return {
      latest: latest ? Math.round(latest.value) : null,
      avg: avg !== null ? Math.round(avg) : null,
      lastTime: last ? slotHhmm(last.slot) : latest ? this.formatDayMonth(latest.date) : null,
      intraday: intradayRaw.map((v) => (v > 0 ? v : null)),
      intradayHasData: intradayRaw.some((v) => v > 0),
      week,
    };
  });

  // -------------------- Poids --------------------
  // Pesées manuelles (WEIGHT_KG, saisies côté Android) : dernière valeur + tendances 7 j / 30 j
  // en courbe (jours sans pesée = gap, jamais interpolé — parité WeightSection Android).
  protected readonly weight = computed(() => {
    const metrics = this.repo.metrics();
    const latest = latestMetric(metrics, 'WEIGHT_KG');
    const week = this.weekSeries(metricByDayCalendar(metrics, 'WEIGHT_KG', this.weekDays));
    return {
      latest: latest ? latest.value.toLocaleString('fr-FR', { maximumFractionDigits: 1 }) : null,
      // Ligne façon FC : moy. = moyenne des pesées des 7 derniers jours ;
      // chip = date de la dernière pesée (toujours affichée).
      avg: week.average !== null ? week.average.toLocaleString('fr-FR', { maximumFractionDigits: 1 }) : null,
      dateLabel: latest ? this.formatDayMonth(latest.date) : null,
      week,
      month: this.monthSeries(metricByDayCalendar(metrics, 'WEIGHT_KG', this.monthDays)),
    };
  });

  // -------------------- Stress --------------------
  // SCORE 0..100 saisi manuellement côté Android (type STRESS — Samsung n'expose pas
  // le stress dans HC), classé en 5 catégories par tranches de 20 (modèle Samsung) :
  // code couleur par catégorie (vert → rouge) sur le score, le libellé et les points
  // des courbes (la courbe reste violette, identité de section).
  private readonly stressLabels = ['Très détendu', 'Détendu', 'Neutre', 'Stressé', 'Très stressé'];
  private stressCategory(score: number): number {
    if (score <= 20) return 0;
    if (score <= 40) return 1;
    if (score <= 60) return 2;
    if (score <= 80) return 3;
    return 4;
  }
  private stressColorToken(score: number): string {
    return [
      'var(--c-medium-green)',
      'var(--c-light-green)',
      'var(--c-yellow-medium)',
      'var(--c-orange-medium)',
      'var(--c-red-medium)',
    ][this.stressCategory(score)];
  }
  protected readonly stress = computed(() => {
    const metrics = this.repo.metrics();
    const latest = latestMetric(metrics, 'STRESS');
    const score = latest ? Math.round(latest.value) : null;
    const week = this.weekSeries(metricByDayCalendar(metrics, 'STRESS', this.weekDays));
    const month = this.monthSeries(metricByDayCalendar(metrics, 'STRESS', this.monthDays));
    const pointColors = (values: number[]) =>
      values.map((v) => (v > 0 ? this.stressColorToken(v) : null));
    return {
      latest: score,
      label: score !== null ? this.stressLabels[this.stressCategory(score)] : '',
      color: score !== null ? this.stressColorToken(score) : null,
      dateLabel: latest ? this.formatDayMonth(latest.date) : null,
      week,
      month,
      weekPointColors: pointColors(week.values),
      monthPointColors: pointColors(month.values),
    };
  });

  // -------------------- Distance & calories --------------------
  protected readonly distance = computed(() => {
    const metrics = this.repo.metrics();
    const latest = latestMetric(metrics, 'DISTANCE');
    return {
      latest: latest ? latest.value : null,
      week: this.weekSeries(metricByDayCalendar(metrics, 'DISTANCE', this.weekDays)),
    };
  });
  /** Unité de distance portée par la dernière mesure (m / km), défaut « m ». */
  protected readonly distanceUnit = computed(
    () => latestMetric(this.repo.metrics(), 'DISTANCE')?.unit || 'm',
  );
  /** Sous-ensemble profil /me (poids/taille/naissance/sexe) pour estimer le BMR. */
  private readonly calorieProfile = computed<CalorieProfile>(() => {
    const u = this.auth.currentUser();
    return {
      weightKg: u?.weightKg ?? null,
      heightCm: u?.heightCm ?? null,
      birthDate: u?.birthDate ?? null,
      sex: u?.sex ?? null,
    };
  });
  /**
   * Répartition calorique 3 lignes (parité Android). Le type stocké donne la sémantique :
   * ACTIVE_CALORIES (montre) → total = actives + BMR ; TOTAL_CALORIES (HC) → actives = max(0, total −
   * BMR). BMR estimé du profil /me (Mifflin-St Jeor, [calorie-math]). Profil incomplet → seul le champ
   * mesuré. + source de la donnée (montre / Health Connect) et série 7 j (actives prioritaires).
   */
  protected readonly energy = computed(() => {
    const metrics = this.repo.metrics();
    const active = latestMetric(metrics, 'ACTIVE_CALORIES');
    const total = latestMetric(metrics, 'TOTAL_CALORIES');
    const distanceMetric = latestMetric(metrics, 'DISTANCE');
    const day = new Date();
    const breakdown: CalorieBreakdown | null = active
      ? fromActive(Math.round(active.value), this.calorieProfile(), day)
      : total
        ? fromTotal(Math.round(total.value), this.calorieProfile(), day)
        : null;
    const source: 'WATCH' | 'HEALTH_CONNECT' | 'NONE' = active
      ? 'WATCH'
      : total || distanceMetric
        ? 'HEALTH_CONNECT'
        : 'NONE';
    // Tendance 7 j : actives (montre) prioritaires, sinon total (HC).
    const weekType = active || !total ? 'ACTIVE_CALORIES' : 'TOTAL_CALORIES';
    return {
      breakdown,
      source,
      week: this.weekSeries(metricByDayCalendar(metrics, weekType, this.weekDays)),
    };
  });
  protected readonly energyHasData = computed(
    () =>
      this.distance().latest !== null ||
      this.energy().breakdown !== null ||
      this.distance().week.hasData ||
      this.energy().week.hasData,
  );
  /**
   * Balance énergétique du JOUR (v1 de « Santé × Nutrition » — la tendance 7 j reste au backlog) :
   * kcal consommées (journal nutrition Dexie, snapshots per-100g) vs dépense totale estimée.
   * null (bloc masqué) si dépense inconnue OU journal du jour vide — sinon un matin sans saisie
   * afficherait un faux déficit géant.
   */
  protected readonly energyBalance = computed(() => {
    const total = this.energy().breakdown?.totalKcal ?? null;
    if (total === null) return null;
    const todayMeals = new Set(
      this.mealRepo
        .meals()
        .filter((m) => m.date === this.today)
        .map((m) => m.uuid),
    );
    let kcal = 0;
    for (const e of this.mealRepo.entries()) {
      if (todayMeals.has(e.mealUUID)) kcal += entryTotals(e).kcal;
    }
    const consumed = Math.round(kcal);
    if (consumed <= 0) return null;
    return { consumed, delta: consumed - total };
  });
  /** Part de l'activité dans la dépense totale (1..100), null si breakdown incomplet ou part nulle. */
  protected activityPct(b: CalorieBreakdown): number | null {
    if (b.activeKcal === null || b.totalKcal === null || b.totalKcal <= 0) return null;
    const pct = Math.round((b.activeKcal / b.totalKcal) * 100);
    return pct > 0 ? pct : null;
  }

  // -------------------- Helpers --------------------

  /** Transforme des paires (date, valeur) en série de chart 7 j (valeurs + quantièmes + moyenne). */
  private weekSeries(pairs: { date: string; value: number }[]): WeekSeries {
    const values = pairs.map((p) => p.value);
    return {
      values,
      // Jours vides (0) → null pour la courbe : le point est absent, pas relié (parité Android).
      lineValues: values.map((v) => (v > 0 ? v : null)),
      labels: this.weekLabels,
      average: averageOfFilledDays(values),
      hasData: values.some((v) => v > 0),
    };
  }

  /** Série 30 jours : repères épars « j/m » (5 positions réparties), labels vides ailleurs
   *  (les catégories restent alignées 1:1 avec les valeurs — parité sparseDayAxisLabels Android). */
  private monthSeries(pairs: { date: string; value: number }[]): WeekSeries {
    const values = pairs.map((p) => p.value);
    const n = pairs.length;
    const marks = new Set([0, Math.floor(n / 4), Math.floor(n / 2), Math.floor((n * 3) / 4), n - 1]);
    const labels = pairs.map((p, i) => {
      if (!marks.has(i)) return '';
      const [, m, d] = p.date.split('-').map(Number);
      return `${d}/${m}`;
    });
    return {
      values,
      lineValues: values.map((v) => (v > 0 ? v : null)),
      labels,
      average: averageOfFilledDays(values),
      hasData: values.some((v) => v > 0),
    };
  }

  /** Date courte localisée « 5 juil. » pour la pastille de dernière pesée. */
  private formatDayMonth(iso: string): string {
    const [y, m, d] = iso.split('-').map(Number);
    return new Date(y, m - 1, d).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' });
  }

  protected formatMinutes(min: number): string {
    return formatHoursMinutes(min);
  }
  protected round1(v: number): number {
    return Math.round(v * 10) / 10;
  }

  /** Date du jour locale "YYYY-MM-DD". */
  private todayIso(): string {
    const d = new Date();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return `${d.getFullYear()}-${mm}-${dd}`;
  }

  /** Pull best-effort au montage + tap ↻ : récupère les dernières données santé du serveur. */
  protected refresh(): void {
    void this.sync.syncAll().catch(() => undefined);
  }

  constructor() {
    this.refresh();

    // Deep-link : si l'URL porte un fragment de section connu (/health#poids), demander son centrage
    // (traité par l'effet ci-dessous dès que le rail est monté).
    const frag = this.route.snapshot.fragment;
    if (isHealthSection(frag)) this.healthNav.requestScroll(frag!);

    // Centrage à la demande (barre basse / drawer / deep-link). L'effet dépend AUSSI de `railEl()` :
    // si la demande précède le montage (deep-link depuis une autre page), il se rejoue automatiquement
    // quand la ref du rail se résout. Le `nonce` de la cible garantit un re-centrage même sur la même
    // section (l'utilisateur avait peut-être scrollé à côté).
    effect(() => {
      const target = this.healthNav.scrollTarget();
      const rail = this.railEl()?.nativeElement;
      if (!target || !rail) return;
      // Glissement programmé : le suivi du scroll est GELÉ jusqu'à l'arrivée (sinon
      // l'actif « défile » sur les sections intermédiaires pendant l'animation).
      this.pendingSlug = target.slug;
      requestAnimationFrame(() => this.centerColumn(rail, target.slug));
    });

    // Après le 1er rendu : suivre la colonne centrée au fil du scroll (met à jour l'item actif de la
    // barre basse / du drawer) + poser l'actif initial. Toute reprise en main manuelle du rail
    // (drag / molette / touch) annule un glissement programmé en cours → le suivi reprend.
    afterNextRender(() => {
      const rail = this.railEl()?.nativeElement;
      if (!rail) return;
      rail.addEventListener('scroll', this.onRailScroll, { passive: true });
      const events = ['pointerdown', 'wheel', 'touchstart'] as const;
      events.forEach((ev) => rail.addEventListener(ev, this.onRailInteract, { passive: true }));
      this.destroyRef.onDestroy(() => {
        rail.removeEventListener('scroll', this.onRailScroll);
        events.forEach((ev) => rail.removeEventListener(ev, this.onRailInteract));
      });
      this.updateActiveFromScroll(rail);
    });
  }

  /** Slug d'un glissement programmé en cours (clic barre basse / drawer / deep-link) ; null = suivi libre. */
  private pendingSlug: string | null = null;

  /** L'utilisateur reprend la main sur le rail : fin du gel du suivi + le snap reprend. */
  private readonly onRailInteract = (): void => {
    this.pendingSlug = null;
    const rail = this.railEl()?.nativeElement;
    if (rail) rail.style.scrollSnapType = '';
  };

  /** Écouteur de scroll du rail : recalcule la colonne centrée (throttlé à une frame). */
  private readonly onRailScroll = (): void => {
    if (this.scrollRaf) return;
    this.scrollRaf = true;
    requestAnimationFrame(() => {
      this.scrollRaf = false;
      const rail = this.railEl()?.nativeElement;
      if (rail) this.updateActiveFromScroll(rail);
    });
  };

  /**
   * Position de scroll (scrollLeft) qui centre la colonne dans le rail, BORNÉE aux extrêmes
   * [0, scrollMax] : les colonnes de bord (Pas, Stress) ne peuvent pas atteindre le centre exact →
   * elles se calent au début / à la fin. Sert au centrage ET à la détection de l'actif (même repère,
   * donc parfaitement cohérents aux bords).
   */
  private targetScrollLeft(rail: HTMLElement, col: HTMLElement): number {
    const railRect = rail.getBoundingClientRect();
    const colRect = col.getBoundingClientRect();
    // Centre de la colonne dans le référentiel de CONTENU du rail (indépendant du scroll courant).
    const colCenterInContent = colRect.left - railRect.left + rail.scrollLeft + colRect.width / 2;
    const ideal = colCenterInContent - rail.clientWidth / 2;
    const max = rail.scrollWidth - rail.clientWidth;
    return Math.max(0, Math.min(max, ideal));
  }

  /** Fait glisser le rail pour amener la colonne `slug` au centre (animé, borné aux extrêmes). */
  private centerColumn(rail: HTMLElement, slug: string): void {
    const col = rail.querySelector<HTMLElement>(`[data-slug="${slug}"]`);
    if (!col) return;
    // Le snap (mandatory) SE BAT avec un scrollTo smooth (Chrome saute au point de
    // snap pendant l'animation → flash visible) : suspendu le temps du glissement
    // programmé, restauré à l'arrivée ou à la reprise en main (cf. updateActiveFromScroll
    // / onRailInteract).
    rail.style.scrollSnapType = 'none';
    rail.scrollTo({ left: this.targetScrollLeft(rail, col), behavior: 'smooth' });
  }

  /**
   * Publie dans [HealthNavService] la colonne « active » = celle dont la position de centrage bornée
   * est la plus proche du scroll courant. Aux BORDS, plusieurs colonnes partagent la même position
   * bornée (à 3 visibles : Pas et FC à 0, Poids et Stress au max) → départage des ex æquo :
   * 1) la section explicitement demandée (clic barre basse / drawer / deep-link),
   * 2) l'actif courant (stabilité au scroll manuel),
   * 3) la première (Pas / Poids restent atteignables au scroll manuel jusqu'au bord).
   */
  private updateActiveFromScroll(rail: HTMLElement): void {
    // Glissement programmé en cours : l'actif est déjà posé sur la cible (requestScroll),
    // on ignore les positions intermédiaires. Le gel se lève à l'arrivée (± 4 px) —
    // ou avant, si l'utilisateur reprend la main (cf. onRailInteract).
    if (this.pendingSlug) {
      const target = rail.querySelector<HTMLElement>(`[data-slug="${this.pendingSlug}"]`);
      if (target && Math.abs(this.targetScrollLeft(rail, target) - rail.scrollLeft) > 4) return;
      this.pendingSlug = null; // arrivé — l'actif est déjà la cible
      rail.style.scrollSnapType = ''; // fin du glissement programmé : le snap reprend
      return;
    }
    const current = rail.scrollLeft;
    const epsilon = 2; // px — égalité de positions bornées
    const dists: [string, number][] = [];
    let bestDist = Infinity;
    rail.querySelectorAll<HTMLElement>('[data-slug]').forEach((col) => {
      const dist = Math.abs(this.targetScrollLeft(rail, col) - current);
      dists.push([col.dataset['slug'] ?? '', dist]);
      if (dist < bestDist) bestDist = dist;
    });
    const candidates = dists.filter(([, d]) => d <= bestDist + epsilon).map(([slug]) => slug);
    if (candidates.length === 0) return;
    const requested = this.healthNav.scrollTarget()?.slug;
    const active = this.healthNav.activeSlug();
    const pick =
      (requested && candidates.includes(requested) ? requested : null) ??
      (candidates.includes(active) ? active : candidates[0]);
    this.healthNav.activeSlug.set(pick);
  }
}
