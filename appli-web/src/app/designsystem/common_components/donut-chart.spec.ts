import { TestBed } from '@angular/core/testing';
import { DonutChartComponent, type DonutSlice } from './donut-chart';

// Même contrainte que RadarChartComponent : le rendu echarts n'est pas testable en jsdom
// (echarts.init / setOption crashent sans contexte canvas 2D). On teste donc les comportements
// observables SANS instancier le graphe :
//   1. l'état vide via le DOM (total <= 0 → placeholder + texte custom, aucun hôte de chart,
//      aucun echarts.init) ;
//   2. le total (somme des parts, négatifs ignorés) qui pilote ce basculement ;
//   3. l'option ECharts produite par buildOption (couleurs résolues : jamais de var() brut remis
//      à echarts). Ce transform est pur → on l'invoque sans détection de changements.

type BuiltDataItem = { name: string; value: number; itemStyle: { color: string } };
type BuiltOption = { series: { data: BuiltDataItem[] }[] };

/** Invoque buildOption sans détecter les changements (pas de detectChanges → pas de canvas). */
function buildOption(slices: DonutSlice[]): BuiltOption {
  const fixture = TestBed.createComponent(DonutChartComponent);
  const comp = fixture.componentInstance as unknown as {
    buildOption(s: DonutSlice[]): BuiltOption;
  };
  return comp.buildOption(slices);
}

describe('DonutChartComponent — anneau de répartition (design system)', () => {
  describe('état vide (dégradation gracieuse, aucun echarts instancié)', () => {
    it('aucune part → placeholder + texte custom, pas de hôte de chart, pas d’init echarts', () => {
      const fixture = TestBed.createComponent(DonutChartComponent);
      fixture.componentRef.setInput('slices', []);
      fixture.componentRef.setInput('emptyText', '—');
      fixture.detectChanges();

      const empty = fixture.nativeElement.querySelector('.dc__empty');
      expect(empty).toBeTruthy();
      expect(empty.textContent.trim()).toBe('—');
      expect(fixture.nativeElement.querySelector('.dc__chart')).toBeNull();
      // Le guard doit empêcher echarts.init quand il n'y a rien à tracer.
      expect((fixture.componentInstance as unknown as { chart: unknown }).chart).toBeNull();
    });

    it('toutes les parts à 0 → total <= 0 → placeholder (pas de chart)', () => {
      const fixture = TestBed.createComponent(DonutChartComponent);
      fixture.componentRef.setInput('slices', [
        { label: 'A', value: 0, color: '#ff0000' },
        { label: 'B', value: 0, color: '#00ff00' },
      ] satisfies DonutSlice[]);
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.dc__empty')).toBeTruthy();
      expect(fixture.nativeElement.querySelector('.dc__chart')).toBeNull();
      expect((fixture.componentInstance as unknown as { chart: unknown }).chart).toBeNull();
    });
  });

  describe('total (pilote l’affichage)', () => {
    it('somme les valeurs positives et ignore les négatives', () => {
      const fixture = TestBed.createComponent(DonutChartComponent);
      fixture.componentRef.setInput('slices', [
        { label: 'A', value: 10, color: '#ff0000' },
        { label: 'B', value: -5, color: '#00ff00' },
        { label: 'C', value: 20, color: '#0000ff' },
      ] satisfies DonutSlice[]);
      const comp = fixture.componentInstance as unknown as { total(): number };
      expect(comp.total()).toBe(30);
    });
  });

  describe('option ECharts (buildOption pur, sans canvas)', () => {
    it('une couleur en token var(...) est résolue — jamais de var() brut remis à echarts', () => {
      const data = buildOption([{ label: 'Protéines', value: 4, color: 'var(--macro-protein)' }])
        .series[0].data;
      expect(data[0].name).toBe('Protéines');
      expect(String(data[0].itemStyle.color)).not.toContain('var(');
    });

    it('une couleur hex est transmise telle quelle', () => {
      const data = buildOption([{ label: 'X', value: 1, color: '#ff0000' }]).series[0].data;
      expect(data[0].itemStyle.color).toBe('#ff0000');
    });
  });
});
