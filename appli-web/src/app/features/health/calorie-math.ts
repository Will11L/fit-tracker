/**
 * Répartition & dérivation calorique du jour — port TypeScript pur de `CalorieBreakdown.kt`
 * (Android, `feature/health/domain`). Aucune dépendance Angular → testable.
 *
 * La sémantique vient du **type de métrique stocké** (`health_metrics.type`), pas d'un flag :
 * - `ACTIVE_CALORIES` (montre — Health Services `CALORIES_DAILY` = actives sur la Watch4) → mesuré =
 *   actives → total = actives + BMR ([fromActive]) ;
 * - `TOTAL_CALORIES` (Health Connect — vrai total BMR inclus) → mesuré = total → actives =
 *   max(0, total − BMR) ([fromTotal]).
 * Le BMR est estimé du profil `/me` (Mifflin-St Jeor). Profil incomplet (poids/taille/âge manquants
 * ou sexe ≠ MALE/FEMALE) → seul le champ mesuré est renseigné (les 2 autres restent null).
 */

/** Les 3 champs sont nullable : seul le champ mesuré est garanti (les autres = estimés/dérivés). */
export interface CalorieBreakdown {
  bmrKcal: number | null; // métabolisme de base ESTIMÉ ; null si profil incomplet
  activeKcal: number | null; // calories actives (MESURÉES montre, ou dérivées total − BMR)
  totalKcal: number | null; // total BMR inclus (MESURÉ HC, ou dérivé actives + BMR)
}

/** Sous-ensemble du profil `/me` utile au BMR. */
export interface CalorieProfile {
  weightKg: number | null;
  heightCm: number | null;
  birthDate: string | null;
  sex: string | null;
}

/** Âge en années à [today] depuis "YYYY-MM-DD" ; null si absente / non parsable / future. */
export function ageYears(birthDate: string | null, today: Date): number | null {
  const raw = birthDate?.trim();
  if (!raw) return null;
  const parts = raw.split('-').map(Number);
  if (parts.length !== 3 || parts.some(Number.isNaN)) return null;
  const [by, bm, bd] = parts;
  const birth = new Date(by, bm - 1, bd);
  if (Number.isNaN(birth.getTime()) || birth > today) return null;
  let age = today.getFullYear() - by;
  const monthDiff = today.getMonth() - (bm - 1);
  if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < bd)) age -= 1;
  return age;
}

/**
 * BMR (kcal/jour) via Mifflin-St Jeor. `null` si un champ manque/est invalide ou si le sexe n'est ni
 * MALE ni FEMALE (formule non définie autrement) :
 *   MALE   = 10·kg + 6,25·cm − 5·âge + 5
 *   FEMALE = 10·kg + 6,25·cm − 5·âge − 161
 */
export function bmr(
  weightKg: number | null,
  heightCm: number | null,
  age: number | null,
  sex: string | null,
): number | null {
  if (weightKg == null || weightKg <= 0) return null;
  if (heightCm == null || heightCm <= 0) return null;
  if (age == null || age < 0 || age > 130) return null;
  const base = 10 * weightKg + 6.25 * heightCm - 5 * age;
  switch (sex?.toUpperCase()) {
    case 'MALE':
      return base + 5;
    case 'FEMALE':
      return base - 161;
    default:
      return null;
  }
}

/** BMR estimé (arrondi) d'un profil à [today], ou null si incomplet. */
function bmrInt(profile: CalorieProfile, today: Date): number | null {
  const value = bmr(profile.weightKg, profile.heightCm, ageYears(profile.birthDate, today), profile.sex);
  return value == null ? null : Math.round(value);
}

/** Depuis un TOTAL mesuré (HC) : actives = max(0, total − BMR). Profil incomplet → total seul. */
export function fromTotal(totalKcal: number, profile: CalorieProfile, today: Date): CalorieBreakdown {
  const b = bmrInt(profile, today);
  if (b == null) return { bmrKcal: null, activeKcal: null, totalKcal };
  return { bmrKcal: b, activeKcal: Math.max(0, totalKcal - b), totalKcal };
}

/** Depuis des ACTIVES mesurées (montre) : total = actives + BMR. Profil incomplet → actives seules. */
export function fromActive(activeKcal: number, profile: CalorieProfile, today: Date): CalorieBreakdown {
  const b = bmrInt(profile, today);
  if (b == null) return { bmrKcal: null, activeKcal, totalKcal: null };
  return { bmrKcal: b, activeKcal, totalKcal: activeKcal + b };
}
