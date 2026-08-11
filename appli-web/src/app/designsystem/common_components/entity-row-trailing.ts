import { Directive, ElementRef, inject } from '@angular/core';

/**
 * `appEntityRowTrailing` — convention partagée de la **zone trailing** des rows d'entité
 * (slot `[trailing]` d'`EntityListRow`) : exercices · muscles · matériel, et toute row au même
 * design (sync + favori + flèche). Un **seul endroit** pour régler l'espacement des icônes
 * (au lieu de dupliquer une classe `.row__trailing` par page) : flex centré avec un `gap` aéré.
 * Les boutons gardent leur taille carrée par défaut (40×40, pas d'étirement à la hauteur de row).
 *
 * Usage : `<span trailing appEntityRowTrailing> … </span>` dans le slot `[trailing]`.
 *
 * Style posé en impératif sur l'hôte (même approche que `RevealIn`) : un directive ne porte pas de
 * feuille de style.
 */
@Directive({
  selector: '[appEntityRowTrailing]',
})
export class EntityRowTrailing {
  constructor() {
    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    host.style.display = 'flex';
    host.style.alignItems = 'center';
    host.style.gap = 'var(--space-3)';
    host.style.paddingLeft = 'var(--space-2)';
  }
}
