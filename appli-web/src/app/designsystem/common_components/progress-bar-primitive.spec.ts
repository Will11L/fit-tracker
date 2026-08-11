import { TestBed } from '@angular/core/testing';
import { ProgressBarPrimitive } from './progress-bar-primitive';

// Le primitif anime le remplissage 0 → valeur : il rend 0 au 1er paint puis pose la vraie valeur au
// paint suivant (afterNextRender) → la transition CSS joue le remplissage. On vérifie ici le contrat
// observable : la largeur finale du remplissage reflète bien la progression (le gate « démarre à 0 »
// ne doit jamais bloquer la valeur réelle) et reste bornée 0..100 %.

async function mount(progress: number): Promise<HTMLElement> {
  const fixture = TestBed.createComponent(ProgressBarPrimitive);
  fixture.componentRef.setInput('progress', progress);
  fixture.detectChanges();
  await fixture.whenStable(); // laisse afterNextRender poser la vraie valeur
  fixture.detectChanges();
  return fixture.nativeElement.querySelector('.fill') as HTMLElement;
}

describe('ProgressBarPrimitive — remplissage animé', () => {
  it('pose la vraie largeur après le 1er rendu (gate 0 → valeur libéré)', async () => {
    expect((await mount(0.5)).style.width).toBe('50%');
  });

  it('borne la progression 0..1 (au-delà de 1 → 100 %, négatif → 0 %)', async () => {
    expect((await mount(1.4)).style.width).toBe('100%');
    expect((await mount(-0.3)).style.width).toBe('0%');
  });
});

describe('ProgressBarPrimitive — repère optionnel (markerAt)', () => {
  it('absent par défaut ; positionné en % (borné 0..1) quand markerAt est fourni', () => {
    const fixture = TestBed.createComponent(ProgressBarPrimitive);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.marker')).toBeNull();

    fixture.componentRef.setInput('markerAt', 0.5);
    fixture.detectChanges();
    const marker = fixture.nativeElement.querySelector('.marker') as HTMLElement;
    expect(marker).not.toBeNull();
    expect(marker.style.left).toBe('50%');

    fixture.componentRef.setInput('markerAt', 1.4);
    fixture.detectChanges();
    expect((fixture.nativeElement.querySelector('.marker') as HTMLElement).style.left).toBe('100%');
  });
});
