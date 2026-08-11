import { TestBed } from '@angular/core/testing';
import { RadarChartComponent, type RadarAxis, type RadarSeries } from './radar-chart';

// Le rendu echarts lui-même n'est pas testable en jsdom (pas de contexte canvas 2D —
// setOption crashe dans zrender), tout comme GoalsAchievementChart / MultiLineChart qui ne sont
// pas testés au rendu. On teste donc 2 comportements observables sans canvas :
//   1. l'état vide via le DOM (le composant n'instancie pas echarts et affiche un placeholder) ;
//   2. l'objet d'option ECharts produit par le composant = ce que le graphe dessine (échelle des
//      axes, remplissage de zone, couleurs résolues). Ce transform est pur (aucun canvas), on
//      l'invoque sans détection de changements donc sans toucher echarts.init.

type Indicator = { name: string; max: number; color?: string };
/** Dégradé radial ECharts (centre→bord) attendu pour le remplissage de zone. */
type RadialGradient = { type: string; x: number; y: number; r: number; colorStops: { offset: number; color: string }[] };
type DataItem = {
  lineStyle: { color: string };
  itemStyle: { color: string };
  areaStyle?: { color: string | RadialGradient };
};
type BuiltOption = { radar: { indicator: Indicator[] }; series: { data: DataItem[] }[]; legend?: unknown };

/** Construit l'option ECharts sans instancier le graphe (pas de detectChanges → pas de canvas). */
function buildOption(
  axes: RadarAxis[],
  series: RadarSeries[],
  areaGradient = true,
  deselected: ReadonlySet<string> = new Set(),
): BuiltOption {
  const fixture = TestBed.createComponent(RadarChartComponent);
  const comp = fixture.componentInstance as unknown as {
    buildOption(a: RadarAxis[], s: RadarSeries[], g: boolean, d: ReadonlySet<string>): BuiltOption;
  };
  return comp.buildOption(axes, series, areaGradient, deselected);
}

describe('RadarChartComponent — diagramme radar / Kiviat (design system)', () => {
  describe('état vide (dégradation gracieuse, aucun echarts instancié)', () => {
    it('axes vides → placeholder + texte custom, pas de hôte de chart, pas d’init echarts', () => {
      const fixture = TestBed.createComponent(RadarChartComponent);
      fixture.componentRef.setInput('axes', []);
      fixture.componentRef.setInput('series', [{ name: 'S', values: [], color: '#ff0000' }]);
      fixture.componentRef.setInput('emptyText', 'Aucune donnée');
      fixture.detectChanges();

      const empty = fixture.nativeElement.querySelector('.rc__empty');
      expect(empty).toBeTruthy();
      expect(empty.textContent.trim()).toBe('Aucune donnée');
      expect(fixture.nativeElement.querySelector('.rc__chart')).toBeNull();
      // Le guard doit empêcher echarts.init quand il n'y a rien à tracer.
      expect((fixture.componentInstance as unknown as { chart: unknown }).chart).toBeNull();
    });

    it('séries vides (mais axes définis) → placeholder affiché', () => {
      const fixture = TestBed.createComponent(RadarChartComponent);
      fixture.componentRef.setInput('axes', [{ label: 'A' }, { label: 'B' }]);
      fixture.componentRef.setInput('series', []);
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.rc__empty')).toBeTruthy();
      expect(fixture.nativeElement.querySelector('.rc__chart')).toBeNull();
    });
  });

  describe('échelle partagée entre axes (axes comparables)', () => {
    it('sans max explicite, tous les axes partagent le max global = ceil(maxValeur × 1.05)', () => {
      const axes = [{ label: 'A' }, { label: 'B' }, { label: 'C' }];
      const series: RadarSeries[] = [
        { name: 'S1', values: [40, 60, 100], color: '#ff0000' },
        { name: 'S2', values: [80, 50, 30], color: '#00ff00' },
      ];
      const opt = buildOption(axes, series);
      // max global = ceil(100 × 1.05) = 105, appliqué à TOUS les axes → comparables.
      expect(opt.radar.indicator.map((i) => i.max)).toEqual([105, 105, 105]);
      expect(opt.radar.indicator.map((i) => i.name)).toEqual(['A', 'B', 'C']);
    });

    it('toutes valeurs à zéro → max plancher à 1 (jamais 0)', () => {
      const opt = buildOption([{ label: 'A' }, { label: 'B' }], [{ name: 'S', values: [0, 0], color: '#ff0000' }]);
      expect(opt.radar.indicator.map((i) => i.max)).toEqual([1, 1]);
    });

    it('un max explicite sur un axe prime sur le max global, pour cet axe seulement', () => {
      const axes: RadarAxis[] = [{ label: 'A', max: 50 }, { label: 'B' }, { label: 'C' }];
      const series: RadarSeries[] = [{ name: 'S', values: [10, 20, 100], color: '#ff0000' }];
      const opt = buildOption(axes, series);
      // A garde son max 50 ; B et C retombent sur le global ceil(100×1.05)=105.
      expect(opt.radar.indicator.map((i) => i.max)).toEqual([50, 105, 105]);
    });
  });

  describe('couleur par axe (indicator color, rétro-compatible)', () => {
    it('un axe sans couleur → pas de champ color sur l’indicator (comportement par défaut inchangé)', () => {
      const axes: RadarAxis[] = [{ label: 'A' }, { label: 'B' }];
      const series: RadarSeries[] = [{ name: 'S', values: [1, 2], color: '#ff0000' }];
      const opt = buildOption(axes, series);
      expect(opt.radar.indicator.every((i) => i.color === undefined)).toBe(true);
    });

    it('une couleur hex par axe est reportée telle quelle sur indicator[].color', () => {
      const axes: RadarAxis[] = [
        { label: 'Glucides', color: '#11d0c4' },
        { label: 'Lipides', color: '#f5a623' },
      ];
      const series: RadarSeries[] = [{ name: 'S', values: [1, 2], color: '#ff0000' }];
      const opt = buildOption(axes, series);
      expect(opt.radar.indicator.map((i) => i.color)).toEqual(['#11d0c4', '#f5a623']);
    });

    it('une couleur en token var(...) est résolue — jamais de var() brut sur l’indicator', () => {
      const axes: RadarAxis[] = [{ label: 'Protéines', color: 'var(--macro-protein)' }];
      const series: RadarSeries[] = [{ name: 'S', values: [1], color: '#ff0000' }];
      const opt = buildOption(axes, series);
      expect(String(opt.radar.indicator[0].color)).not.toContain('var(');
    });
  });

  describe('rendu par série', () => {
    it('remplissage de zone (areaStyle) présent uniquement pour les séries area:true', () => {
      const axes = [{ label: 'A' }, { label: 'B' }];
      const series: RadarSeries[] = [
        { name: 'Filled', values: [1, 2], color: '#ff0000', area: true },
        { name: 'Line', values: [2, 1], color: '#00ff00', area: false },
      ];
      const data = buildOption(axes, series).series[0].data;
      expect(data[0].areaStyle).toBeTruthy();
      expect(data[1].areaStyle).toBeUndefined();
    });

    it('couleur en token CSS var(...) est résolue — jamais de var() brut remis à echarts', () => {
      const axes = [{ label: 'A' }, { label: 'B' }];
      const series: RadarSeries[] = [{ name: 'S', values: [1, 2], color: 'var(--macro-protein)', area: true }];
      const item = buildOption(axes, series).series[0].data[0];
      // echarts ne sait pas interpréter "var(--x)" : la chaîne doit avoir été résolue en couleur.
      expect(String(item.lineStyle.color)).not.toContain('var(');
      expect(String(item.itemStyle.color)).not.toContain('var(');
      // areaStyle (dégradé radial par défaut) : ses colorStops dérivent de la couleur résolue en rgba(..).
      const fill = item.areaStyle?.color as RadialGradient;
      expect(fill.colorStops.every((s) => /^rgba\(/.test(s.color))).toBe(true);
      expect(fill.colorStops.some((s) => s.color.includes('var('))).toBe(false);
    });
  });

  describe('remplissage en dégradé radial (areaGradient)', () => {
    it('par défaut, le remplissage area:true est un dégradé RADIAL centré (x=y=r=0.5), centre→bord', () => {
      const axes = [{ label: 'A' }, { label: 'B' }];
      const series: RadarSeries[] = [{ name: 'S', values: [1, 2], color: '#ff0000', area: true }];
      const fill = buildOption(axes, series).series[0].data[0].areaStyle?.color as RadialGradient;
      expect(fill.type).toBe('radial');
      expect([fill.x, fill.y, fill.r]).toEqual([0.5, 0.5, 0.5]);
      // 2 arrêts : offset 0 (centre) → offset 1 (bord).
      expect(fill.colorStops.map((s) => s.offset)).toEqual([0, 1]);
      // Centre plus transparent que le bord → alpha croissant du centre vers l'extérieur.
      const alpha = (c: string) => Number(c.replace(/^rgba\([^)]*,\s*([\d.]+)\)$/, '$1'));
      expect(alpha(fill.colorStops[0].color)).toBeLessThan(alpha(fill.colorStops[1].color));
    });

    it('areaGradient=false → aplat translucide rétro-compatible (rgba simple, pas de dégradé)', () => {
      const axes = [{ label: 'A' }, { label: 'B' }];
      const series: RadarSeries[] = [{ name: 'S', values: [1, 2], color: '#ff0000', area: true }];
      const color = buildOption(axes, series, false).series[0].data[0].areaStyle?.color;
      expect(typeof color).toBe('string');
      expect(String(color)).toMatch(/^rgba\(/);
    });

    it('plusieurs séries area:true → chaque dégradé radial dérive de SA propre couleur (pas partagée)', () => {
      // Cas réel "radar macros" : 2 séries remplies comparées (ex. aujourd'hui vs objectif).
      const axes = [{ label: 'A' }, { label: 'B' }];
      const series: RadarSeries[] = [
        { name: 'Aujourd’hui', values: [1, 2], color: '#ff0000', area: true },
        { name: 'Objectif', values: [2, 1], color: '#00ff00', area: true },
      ];
      const data = buildOption(axes, series).series[0].data;
      const g0 = data[0].areaStyle?.color as RadialGradient;
      const g1 = data[1].areaStyle?.color as RadialGradient;
      // Deux dégradés radiaux indépendants…
      expect(g0.type).toBe('radial');
      expect(g1.type).toBe('radial');
      // …chacun dérivé de la couleur de SA série (base RGB distincte) — une boucle qui figerait
      // une seule couleur partagée serait attrapée ici.
      const rgb = (c: string) =>
        c.replace(/^rgba\(|\)$/g, '').split(',').slice(0, 3).map((n) => n.trim()).join(',');
      expect(g0.colorStops.every((s) => rgb(s.color) === '255,0,0')).toBe(true);
      expect(g1.colorStops.every((s) => rgb(s.color) === '0,255,0')).toBe(true);
    });
  });

  describe('légende (HTML cliquable, sous le graphe)', () => {
    /** Composant câblé avec des séries, sans detectChanges (pas d'echarts) — pour lire legendItems/toggle. */
    function legendComp(series: RadarSeries[]) {
      const fixture = TestBed.createComponent(RadarChartComponent);
      fixture.componentRef.setInput('axes', [{ label: 'A' }, { label: 'B' }]);
      fixture.componentRef.setInput('series', series);
      return fixture.componentInstance as unknown as {
        legendItems(): { name: string; color: string; selected: boolean }[];
        toggleSeries(name: string): void;
      };
    }

    it('legendItems : une entrée par série, toutes sélectionnées au départ, couleur en token brut conservé', () => {
      const c = legendComp([
        { name: 'Consommé', values: [1, 2], color: 'var(--macro-kcal)' },
        { name: 'Cible', values: [2, 1], color: '#888888' },
      ]);
      const items = c.legendItems();
      expect(items.map((i) => i.name)).toEqual(['Consommé', 'Cible']);
      expect(items.every((i) => i.selected)).toBe(true);
      // La couleur reste le token brut : le navigateur la résout via [style], pas le composant.
      expect(items[0].color).toBe('var(--macro-kcal)');
    });

    it('toggleSeries masque puis ré-affiche une série (état observable de la légende)', () => {
      const c = legendComp([
        { name: 'Consommé', values: [1, 2], color: '#aa0000' },
        { name: 'Cible', values: [2, 1], color: '#00aa00' },
      ]);
      c.toggleSeries('Cible');
      expect(c.legendItems().find((i) => i.name === 'Cible')?.selected).toBe(false);
      expect(c.legendItems().find((i) => i.name === 'Consommé')?.selected).toBe(true);
      c.toggleSeries('Cible');
      expect(c.legendItems().find((i) => i.name === 'Cible')?.selected).toBe(true);
    });

    it('une série désélectionnée est retirée du data ECharts (polygone masqué) ; l’échelle reste sur toutes', () => {
      const axes = [{ label: 'A' }, { label: 'B' }];
      const series: RadarSeries[] = [
        { name: 'Consommé', values: [10, 20], color: '#aa0000' },
        { name: 'Cible', values: [100, 50], color: '#00aa00' },
      ];
      expect(buildOption(axes, series).series[0].data.length).toBe(2);
      const hidden = buildOption(axes, series, true, new Set(['Cible']));
      expect(hidden.series[0].data.length).toBe(1);
      // Max global calculé sur TOUTES les séries (incl. Cible=100) → échelle stable : ceil(100×1.05)=105.
      expect(hidden.radar.indicator.map((i) => i.max)).toEqual([105, 105]);
    });
  });
});
