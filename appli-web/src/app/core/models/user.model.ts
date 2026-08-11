/** Forme renvoyée par GET /api/v1/me (UserOut serveur, alias camelCase). */
export interface CurrentUser {
  id: number;
  username: string;
  isAdmin: boolean;
  firstName: string | null;
  lastName: string | null;
  email: string;
  /** Bio (livré 2026-05-11 côté serveur) — affichée sur la page Profil. */
  birthDate: string | null;
  sex: 'MALE' | 'FEMALE' | 'OTHER' | null;
  heightCm: number | null;
  weightKg: number | null;
}

/** Body de PATCH /api/v1/me/profile (MeProfileUpdate serveur) — champs absents = inchangés. */
export interface MeProfileUpdate {
  email?: string | null;
  firstName?: string | null;
  lastName?: string | null;
  birthDate?: string | null;
  sex?: 'MALE' | 'FEMALE' | 'OTHER' | null;
  heightCm?: number | null;
  weightKg?: number | null;
}
