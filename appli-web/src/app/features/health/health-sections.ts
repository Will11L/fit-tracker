/**
 * Source unique des sections du hub Santé (`/health`) — partagée par la page (colonnes du rail
 * horizontal + ancres de deep-link), la barre basse (mode Santé : 1 bouton par section) et le drawer
 * (section Santé : 1 item par section). Ordre = ordre d'affichage des colonnes de gauche à droite.
 *
 * `slug` = ancre stable (fragment d'URL `/health#<slug>`, jamais traduit) ; `title` = libellé FR ;
 * `icon` = ligature Material Symbols Outlined (cf. AppIcon) ; `color` = couleur d'identité de la
 * section (token CSS), partagée par le divider de tête de colonne (page) ET le fond du bouton actif de
 * la barre basse (miroir de la HealthIconBar Android : onglet actif = fond couleur de section).
 */
export interface HealthSectionRef {
  slug: string;
  title: string;
  icon: string;
  color: string;
}

export const HEALTH_SECTIONS: readonly HealthSectionRef[] = [
  { slug: 'pas', title: 'Pas', icon: 'directions_walk', color: 'var(--c-light-green)' },
  { slug: 'fc', title: 'Fréquence cardiaque', icon: 'cardiology', color: 'var(--c-orange-medium)' },
  { slug: 'sommeil', title: 'Sommeil', icon: 'bedtime', color: 'var(--app-primary-action)' },
  { slug: 'spo2', title: 'SpO2', icon: 'spo2', color: 'var(--c-light-blue)' },
  {
    slug: 'energie',
    title: 'Distance & calories',
    icon: 'local_fire_department',
    color: 'var(--c-turquoise)',
  },
  { slug: 'poids', title: 'Poids', icon: 'monitor_weight', color: 'var(--c-yellow-medium)' },
  { slug: 'stress', title: 'Stress', icon: 'psychology', color: 'var(--c-bright-purple)' },
];

/** Vrai si `slug` désigne une section connue du hub Santé (garde-fou deep-link / fragment). */
export function isHealthSection(slug: string | null | undefined): boolean {
  return !!slug && HEALTH_SECTIONS.some((s) => s.slug === slug);
}
