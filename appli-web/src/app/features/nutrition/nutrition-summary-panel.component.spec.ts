import { TestBed } from '@angular/core/testing';
import { NutritionSummaryPanel, type MacroAmounts } from './nutrition-summary-panel';
import { type MicroNutrients } from './micros';

// Le PANNEAU réutilisable lui-même (catalogue T5 / recettes T7) : on vérifie le CÂBLAGE piloté par
// les props (display, micros, showMicros, targets, unitSuffix) = le contrat de réutilisabilité.
// C'est le comportement observable que les helpers purs (déjà couverts dans
// nutrition-summary-panel.spec.ts) n'expriment pas. Les modes `radar` et `donut` ne sont PAS montés
// avec des données non nulles : ils instancient echarts (pas de contexte canvas en jsdom, cf.
// radar-chart.spec / donut-chart.spec) ; leurs données sont couvertes par les helpers (macroRadarData,
// macroEnergyShares). Le mode donut est vérifié ici (a) sur ses computeds câblés (lus sans
// detectChanges → pas d'echarts) et (b) à l'état vide monté (placeholder, pas d'echarts.init).

const MACROS: MacroAmounts = { protein: 30, carbs: 60, fat: 20, fiber: 10 };

const ZERO_MICROS: MicroNutrients = {
  ironPer100g: null,
  calciumPer100g: null,
  magnesiumPer100g: null,
  zincPer100g: null,
  potassiumPer100g: null,
  sodiumPer100g: null,
  vitaminCPer100g: null,
  vitaminDPer100g: null,
  vitaminB12Per100g: null,
  vitaminAPer100g: null,
};

function mount(inputs: Record<string, unknown>) {
  const fixture = TestBed.createComponent(NutritionSummaryPanel);
  // inputs requis
  fixture.componentRef.setInput('kcal', inputs['kcal'] ?? 0);
  fixture.componentRef.setInput('macros', inputs['macros'] ?? MACROS);
  for (const [k, v] of Object.entries(inputs)) {
    if (k === 'kcal' || k === 'macros') continue;
    fixture.componentRef.setInput(k, v);
  }
  fixture.detectChanges();
  return fixture;
}

describe('NutritionSummaryPanel — câblage du composant (panneau réutilisable)', () => {
  describe('en-tête calories', () => {
    it('affiche les kcal du profil ; cible et suffixe d’unité absents par défaut', () => {
      const el = mount({ kcal: 250, macros: MACROS }).nativeElement;
      expect(el.querySelector('.nsp__kcal-value').textContent.trim()).toBe('250 kcal');
      expect(el.querySelector('.nsp__kcal-target')).toBeNull();
      expect(el.querySelector('.nsp__unit')).toBeNull();
    });

    it('affiche cible kcal et suffixe d’unité seulement si fournis', () => {
      const el = mount({
        kcal: 250,
        macros: MACROS,
        targets: { kcal: 2000 },
        unitSuffix: '/ 100 g',
      }).nativeElement;
      expect(el.querySelector('.nsp__kcal-target').textContent).toContain('2000');
      expect(el.querySelector('.nsp__unit').textContent.trim()).toBe('/ 100 g');
    });
  });

  describe('mode barre (défaut)', () => {
    it('rend 4 barres macros et aucune section micros quand micros non fournis', () => {
      const el = mount({ kcal: 250, macros: MACROS }).nativeElement;
      expect(el.querySelectorAll('.nsp__row').length).toBe(4); // carbs/fat/protein/fiber
      expect(el.querySelectorAll('app-progress-bar-primitive').length).toBe(4);
      expect(el.querySelector('.nsp__subtitle')).toBeNull();
    });

    it('micros fournis → sous-titre « Micros » + 10 barres micros (14 lignes au total)', () => {
      const el = mount({
        kcal: 250,
        macros: MACROS,
        micros: { ...ZERO_MICROS, ironPer100g: 7, calciumPer100g: 400 },
      }).nativeElement;
      expect(el.querySelector('.nsp__subtitle').textContent).toContain('Micros');
      expect(el.querySelectorAll('.nsp__row').length).toBe(14); // 4 macros + 10 micros
    });

    it('showMicros=false → section micros masquée même si des micros sont fournis', () => {
      const el = mount({
        kcal: 250,
        macros: MACROS,
        micros: { ...ZERO_MICROS, ironPer100g: 7 },
        showMicros: false,
      }).nativeElement;
      expect(el.querySelector('.nsp__subtitle')).toBeNull();
      expect(el.querySelectorAll('.nsp__row').length).toBe(4); // que les macros
    });

    it('Sodium au-dessus du plafond → ligne en alerte + signal non chromatique (icône ⚠)', () => {
      const el = mount({
        kcal: 0,
        macros: MACROS,
        micros: { ...ZERO_MICROS, sodiumPer100g: 2500 },
      }).nativeElement;
      // Une seule ligne en alerte (Sodium) : perceptible indépendamment de la couleur (daltonisme).
      const alertRows = el.querySelectorAll('.nsp__row--alert');
      expect(alertRows.length).toBe(1);
      expect(alertRows[0].querySelector('app-icon .ms').textContent.trim()).toBe('warning');
    });
  });

  describe('mode ligne', () => {
    it('rend une ligne macros compacte (G/L/P/F), aucune barre, + ligne micros présents', () => {
      const el = mount({
        kcal: 250,
        macros: MACROS,
        display: 'line',
        micros: { ...ZERO_MICROS, calciumPer100g: 120 },
      }).nativeElement;
      expect(el.querySelectorAll('app-progress-bar-primitive').length).toBe(0);
      const line = el.querySelector('.nsp__line').textContent;
      expect(line).toContain('G 60');
      expect(line).toContain('L 20');
      expect(line).toContain('P 30');
      expect(line).toContain('F 10');
      expect(el.querySelector('.nsp__micro-line').textContent).toContain('Ca 120 mg');
    });

    it('micros fournis mais tous nuls → sous-titre + indice « Aucun micronutriment renseigné. »', () => {
      const el = mount({
        kcal: 0,
        macros: MACROS,
        display: 'line',
        micros: ZERO_MICROS,
      }).nativeElement;
      expect(el.querySelector('.nsp__subtitle')).toBeTruthy(); // hasMicros = objet non null
      expect(el.querySelector('.nsp__micro-line')).toBeNull();
      expect(el.querySelector('.nsp__hint').textContent.trim()).toBe('Aucun micronutriment renseigné.');
    });
  });

  describe('mode donut (répartition macro en kcal)', () => {
    // Type des computeds câblés du donut (lus directement, sans monter le template → pas d'echarts).
    interface DonutProbe {
      donut(): { label: string; value: number; color: string }[];
      donutTotalKcal(): number;
    }

    it('alimente le donut en kcal par macro (pas en grammes), ordre canonique G/L/P/F + total central', () => {
      // Lecture des computeds SANS detectChanges : le donut est une répartition d'ÉNERGIE (Atwater),
      // distinct des barres qui sont en grammes — c'est le contrat que ce mode introduit.
      const fixture = TestBed.createComponent(NutritionSummaryPanel);
      fixture.componentRef.setInput('kcal', 560);
      fixture.componentRef.setInput('macros', MACROS); // 60 G, 20 L, 30 P, 10 F
      fixture.componentRef.setInput('display', 'donut');
      const probe = fixture.componentInstance as unknown as DonutProbe;

      expect(probe.donut().map((s) => s.value)).toEqual([240, 180, 120, 20]); // 4·60, 9·20, 4·30, 2·10
      expect(probe.donutTotalKcal()).toBe(560);
    });

    it('profil sans énergie → donut à l’état vide (placeholder, pas d’echarts), aucune barre ni légende', () => {
      // macros tous nuls → total kcal = 0 → le donut DS rend son placeholder (montable en jsdom :
      // pas d'echarts.init), la légende % est masquée, et les barres ne sont pas rendues (display=donut).
      const el = mount({
        kcal: 0,
        macros: { protein: 0, carbs: 0, fat: 0, fiber: 0 },
        display: 'donut',
      }).nativeElement;
      expect(el.querySelector('app-donut-chart')).toBeTruthy();
      expect(el.querySelector('app-donut-chart .dc__empty')).toBeTruthy(); // placeholder, pas de chart
      expect(el.querySelector('app-donut-chart .dc__chart')).toBeNull();
      expect(el.querySelector('.nsp__donut-legend')).toBeNull(); // légende masquée si total = 0
      expect(el.querySelectorAll('.nsp__row').length).toBe(0); // pas les barres : on est en donut
    });
  });
});
