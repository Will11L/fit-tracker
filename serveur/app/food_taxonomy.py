# app/food_taxonomy.py
#
# Taxonomie des aliments (feature [Categories d'aliments], vague serveur S1).
# Module PUR (sans DB) : importable par scripts/import_ciqual.py + testable seul.
#
# 2 niveaux (meme motif que la refonte muscles, version a 2 niveaux) :
#   - Groupe : colonne stockee `foods.food_group`, UPPER_CASE (politique 11),
#     ~18 valeurs curatees (FOOD_GROUPS).
#   - Regne : NON stocke -> derive du groupe (realm_of) : ANIMALE / VEGETALE /
#     COMPLEMENT / AUTRE.
#
# La distinction viande rouge / blanche reste un AXE DE GROUPE dans la taxonomie
# projet (VIANDE_ROUGE / VIANDE_BLANCHE, pas de 3e niveau stocke). Mais pour la
# trancher a l'import, on lit en ENTREE un 3e niveau CIQUAL (alim_ssssgrp :
# poulet/dinde/boeuf/porc/abats...) : le 2e niveau du CIQUAL 2025 reel est trop
# grossier (viandes crues/cuites/charcuteries, sans mention de volaille).
#
# CIQUAL ne contient que des aliments bruts -> classify_ciqual ne renvoie JAMAIS
# COMPLEMENT_* (ceux-ci viennent d'OFF / CUSTOM, mappes hors de ce module).

from __future__ import annotations

import unicodedata

# -------------------- Regnes (derives, jamais stockes) --------------------
REALM_ANIMALE = "ANIMALE"
REALM_VEGETALE = "VEGETALE"
REALM_COMPLEMENT = "COMPLEMENT"
REALM_AUTRE = "AUTRE"

# -------------------- Groupes curates (colonne food_group) --------------------
# UPPER_CASE (politique 11). ~18 valeurs. Ordre = ordre d'affichage suggere
# (animaux, vegetaux, transformes, complements, fallback).
FOOD_GROUPS: tuple[str, ...] = (
    "VIANDE_ROUGE",
    "VIANDE_BLANCHE",
    "POISSON",
    "FRUITS_DE_MER",
    "OEUF",
    "LAITAGE",
    "LEGUMINEUSE",
    "LEGUME",
    "FRUIT",
    "CEREALE_FECULENT",
    "NOIX_GRAINE",
    "MATIERE_GRASSE",
    "PRODUIT_SUCRE",
    "BOISSON",
    "PLAT_COMPOSE",
    "COMPLEMENT_MACRO",
    "COMPLEMENT_MICRO",
    "AUTRE",
)

# Derivation groupe -> regne (cf. feature : viandes/poisson/fruits de mer/oeuf/
# laitage -> ANIMALE ; legumineuse/legume/fruit/cereale/noix -> VEGETALE ;
# complements -> COMPLEMENT ; matiere grasse/sucre/boisson/plat compose/autre ->
# AUTRE).
_REALM_BY_GROUP: dict[str, str] = {
    "VIANDE_ROUGE": REALM_ANIMALE,
    "VIANDE_BLANCHE": REALM_ANIMALE,
    "POISSON": REALM_ANIMALE,
    "FRUITS_DE_MER": REALM_ANIMALE,
    "OEUF": REALM_ANIMALE,
    "LAITAGE": REALM_ANIMALE,
    "LEGUMINEUSE": REALM_VEGETALE,
    "LEGUME": REALM_VEGETALE,
    "FRUIT": REALM_VEGETALE,
    "CEREALE_FECULENT": REALM_VEGETALE,
    "NOIX_GRAINE": REALM_VEGETALE,
    "MATIERE_GRASSE": REALM_AUTRE,
    "PRODUIT_SUCRE": REALM_AUTRE,
    "BOISSON": REALM_AUTRE,
    "PLAT_COMPOSE": REALM_AUTRE,
    "COMPLEMENT_MACRO": REALM_COMPLEMENT,
    "COMPLEMENT_MICRO": REALM_COMPLEMENT,
    "AUTRE": REALM_AUTRE,
}


def realm_of(food_group: str | None) -> str:
    """Regne derive du groupe curate. None / inconnu -> AUTRE."""
    if not food_group:
        return REALM_AUTRE
    return _REALM_BY_GROUP.get(food_group.upper(), REALM_AUTRE)


# ============================================================================
# Mapping CIQUAL (alim_grp_nom_fr / alim_ssgrp_nom_fr) -> groupe curate
# ============================================================================

def _norm(s: str | None) -> str:
    """Minuscule + accents retires : matching tolerant des libelles CIQUAL.

    Les ligatures oe/ae (œ/æ) ne se decomposent pas sous NFKD -> remplacees a la
    main, sinon 'œufs' ne matcherait jamais le mot-cle 'oeuf'.
    """
    nfkd = unicodedata.normalize("NFKD", s or "")
    stripped = "".join(c for c in nfkd if not unicodedata.combining(c)).lower().strip()
    return stripped.replace("œ", "oe").replace("æ", "ae")


def _any(text: str, *needles: str) -> bool:
    return any(n in text for n in needles)


def _classify_animal_subgroup(ssgrp: str, ssssgrp: str = "") -> str:
    """Sous-groupe du groupe CIQUAL 'viandes, oeufs, poissons et assimiles'.

    Poisson / fruits de mer / oeuf / substitut vegetal se tranchent au 2e niveau
    (ssgrp) seul. La distinction viande rouge/blanche se joue au 3e niveau CIQUAL
    (ssssgrp : poulet/dinde/boeuf/porc/abats...) quand il est fourni : le 2e
    niveau du CIQUAL 2025 reel est trop grossier (viandes crues/cuites). A defaut
    de 3e niveau (None / vide / '-'), fallback sur le 2e (volaille -> blanche,
    sinon rouge generique) -> retro-compatible avec les appels a 2 arguments.
    """
    # Non-viande : determinable au 2e niveau seul.
    if _any(ssgrp, "mollusque", "crustace", "fruits de mer"):
        return "FRUITS_DE_MER"
    if _any(ssgrp, "poisson"):
        return "POISSON"
    if _any(ssgrp, "oeuf"):
        return "OEUF"
    if _any(ssgrp, "substitut"):  # substituts de produits carnes (vegetaux)
        return "LEGUMINEUSE"

    # Viande : rouge/blanche tranchee au 3e niveau (alim_ssssgrp) si disponible.
    # Blanc d'abord pour que 'gibier a plume' batte le 'gibier' (a poil) generique.
    if _any(ssssgrp, "poulet", "dinde", "volaille", "pintade", "lapin", "gibier a plume"):
        return "VIANDE_BLANCHE"
    if _any(ssssgrp, "boeuf", "veau", "porc", "agneau", "mouton", "cheval",
            "gibier", "abat", "charcuterie", "autres viandes", "viande"):
        return "VIANDE_ROUGE"

    # Pas d'info exploitable au 3e niveau : fallback sur le 2e (retro-compat).
    if _any(ssgrp, "volaille", "lapin", "poulet", "dinde", "gibier a plume"):
        return "VIANDE_BLANCHE"
    if _any(ssgrp, "charcuterie", "abat", "viande"):
        return "VIANDE_ROUGE"
    return "AUTRE"  # sous-groupe inconnu d'un groupe mixte -> indeterminable


def _classify_plant_subgroup(ssgrp: str) -> str:
    """Sous-groupe du groupe CIQUAL 'fruits, legumes, legumineuses et oleagineux'."""
    if _any(ssgrp, "legumineuse", "legume sec", "legumes secs"):
        return "LEGUMINEUSE"
    if _any(ssgrp, "fruits a coque", "oleagineu", "graine", "noix"):
        return "NOIX_GRAINE"
    if _any(ssgrp, "pomme de terre", "tubercule"):
        return "CEREALE_FECULENT"
    if _any(ssgrp, "fruit"):  # apres 'fruits a coque' (deja capte ci-dessus)
        return "FRUIT"
    if _any(ssgrp, "legume", "champignon", "algue", "herbe", "aromate"):
        return "LEGUME"
    return "LEGUME"  # fallback du groupe vegetal


def classify_ciqual(
    alim_grp_nom_fr: str | None,
    alim_ssgrp_nom_fr: str | None,
    alim_ssssgrp_nom_fr: str | None = None,
) -> str:
    """Mappe les libelles CIQUAL (groupe + sous-groupe [+ 3e niveau]) vers un groupe curate.

    Best-effort par matching de mots-cles normalises (insensible casse/accents),
    robuste aux variations de libelle. Fallback `AUTRE` si rien ne matche.
    Le regne se derive ensuite via realm_of().

    `alim_ssssgrp_nom_fr` (3e niveau CIQUAL) est OPTIONNEL : il ne sert qu'a
    trancher viande rouge/blanche dans le groupe mixte animal. Absent/None ->
    comportement a 2 niveaux (retro-compatible).
    """
    grp = _norm(alim_grp_nom_fr)
    ssgrp = _norm(alim_ssgrp_nom_fr)
    ssssgrp = _norm(alim_ssssgrp_nom_fr)

    # Groupe mixte animal : resolution fine au sous-groupe (rouge/blanche/...).
    if "poisson" in grp and "viande" in grp:
        return _classify_animal_subgroup(ssgrp, ssssgrp)

    # Groupe vegetal large : resolution au sous-groupe (legume/fruit/noix/...).
    if "legume" in grp and ("oleagineux" in grp or "legumineuse" in grp):
        return _classify_plant_subgroup(ssgrp)

    if "laitier" in grp or grp.startswith("lait"):
        return "LAITAGE"
    if "cereali" in grp:  # 'produits cerealiers'
        return "CEREALE_FECULENT"
    if "sucre" in grp or "glace" in grp or "sorbet" in grp:
        return "PRODUIT_SUCRE"
    if "matiere" in grp and "grasse" in grp:
        return "MATIERE_GRASSE"
    if grp.startswith("eau") or "boisson" in grp:
        return "BOISSON"
    if "plat" in grp or "entree" in grp:
        return "PLAT_COMPOSE"

    # Aides culinaires, aliments infantiles, ingredients divers, inconnu -> AUTRE.
    return "AUTRE"
