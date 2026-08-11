"""Tests purs du module de taxonomie des aliments (feature Categories d'aliments,
vague serveur S1). Aucune DB : mapping CIQUAL -> groupe curate -> regne.

Couvre :
- classify_ciqual : groupes/sous-groupes CIQUAL -> groupe curate (dont regle
  viande rouge/blanche au sous-groupe), tolerant casse/accents, fallback AUTRE.
- realm_of : derivation groupe -> regne (ANIMALE/VEGETALE/COMPLEMENT/AUTRE).
- coherence : tout groupe de FOOD_GROUPS a un regne defini.
"""
from app.food_taxonomy import (
    FOOD_GROUPS,
    REALM_ANIMALE,
    REALM_AUTRE,
    REALM_COMPLEMENT,
    REALM_VEGETALE,
    classify_ciqual,
    realm_of,
)

_MEAT_FISH = "viandes, œufs, poissons et assimilés"
_PLANTS = "fruits, légumes, légumineuses et oléagineux"


# ============================================================================
# classify_ciqual — groupe animal (rouge / blanche / poisson / fruits de mer / oeuf)
# ============================================================================

def test_classify_viande_rouge_boucherie():
    assert classify_ciqual(_MEAT_FISH, "viandes crues") == "VIANDE_ROUGE"
    assert classify_ciqual(_MEAT_FISH, "viandes cuites") == "VIANDE_ROUGE"


def test_classify_viande_rouge_charcuterie_et_abats():
    assert classify_ciqual(_MEAT_FISH, "charcuteries et assimilés") == "VIANDE_ROUGE"
    assert classify_ciqual(_MEAT_FISH, "abats") == "VIANDE_ROUGE"


def test_classify_viande_blanche_volaille():
    # La distinction rouge/blanche se joue au sous-groupe (axe variete).
    assert classify_ciqual(_MEAT_FISH, "volailles et gibiers") == "VIANDE_BLANCHE"
    assert classify_ciqual(_MEAT_FISH, "viandes de volaille cuites") == "VIANDE_BLANCHE"
    assert classify_ciqual(_MEAT_FISH, "lapin") == "VIANDE_BLANCHE"


def test_classify_poisson_et_fruits_de_mer():
    assert classify_ciqual(_MEAT_FISH, "poissons crus") == "POISSON"
    assert classify_ciqual(_MEAT_FISH, "poissons cuits") == "POISSON"
    assert classify_ciqual(_MEAT_FISH, "mollusques et crustacés") == "FRUITS_DE_MER"


def test_classify_oeuf():
    assert classify_ciqual(_MEAT_FISH, "œufs") == "OEUF"
    assert classify_ciqual(_MEAT_FISH, "oeufs et dérivés") == "OEUF"


def test_classify_substitut_de_viande_vegetal():
    assert classify_ciqual(_MEAT_FISH, "substituts de produits carnés") == "LEGUMINEUSE"


# ============================================================================
# classify_ciqual — viande rouge/blanche via le 3e niveau CIQUAL (alim_ssssgrp)
# ============================================================================
# Le CIQUAL 2025 reel n'expose la volaille qu'au 3e niveau : le 2e niveau du
# groupe viandes est trop grossier (viandes crues/cuites/charcuteries).

def test_classify_viande_blanche_via_ssssgrp():
    # ssgrp grossier ("viandes cuites") + 3e niveau volaille -> BLANCHE.
    assert classify_ciqual(_MEAT_FISH, "viandes cuites", "poulet") == "VIANDE_BLANCHE"
    assert classify_ciqual(_MEAT_FISH, "viandes crues", "dinde") == "VIANDE_BLANCHE"
    assert classify_ciqual(_MEAT_FISH, "viandes crues", "volaille") == "VIANDE_BLANCHE"
    assert classify_ciqual(_MEAT_FISH, "viandes cuites", "pintade") == "VIANDE_BLANCHE"
    assert classify_ciqual(_MEAT_FISH, "viandes cuites", "gibier à plume") == "VIANDE_BLANCHE"


def test_classify_viande_rouge_via_ssssgrp():
    assert classify_ciqual(_MEAT_FISH, "viandes crues", "boeuf et veau") == "VIANDE_ROUGE"
    assert classify_ciqual(_MEAT_FISH, "viandes cuites", "porc") == "VIANDE_ROUGE"
    assert classify_ciqual(_MEAT_FISH, "viandes crues", "agneau et mouton") == "VIANDE_ROUGE"
    assert classify_ciqual(_MEAT_FISH, "abats", "abats") == "VIANDE_ROUGE"
    assert classify_ciqual(_MEAT_FISH, "charcuteries et assimilés", "charcuteries") == "VIANDE_ROUGE"
    assert classify_ciqual(_MEAT_FISH, "viandes crues", "autres viandes") == "VIANDE_ROUGE"
    # 'gibier' (a poil) sans precision -> ROUGE (cf. 'gibier a plume' -> blanche).
    assert classify_ciqual(_MEAT_FISH, "viandes crues", "gibier") == "VIANDE_ROUGE"


def test_classify_viande_ssssgrp_vide_fallback_rouge_generique():
    # 3e niveau vide ('-' ou '') -> fallback ROUGE generique au 2e niveau.
    assert classify_ciqual(_MEAT_FISH, "viandes crues", "-") == "VIANDE_ROUGE"
    assert classify_ciqual(_MEAT_FISH, "viandes cuites", "") == "VIANDE_ROUGE"


def test_classify_ciqual_retro_compatible_deux_args():
    # Appel a 2 arguments (3e niveau absent) : comportement historique inchange.
    assert classify_ciqual(_MEAT_FISH, "viandes crues") == "VIANDE_ROUGE"
    assert classify_ciqual(_MEAT_FISH, "volailles et gibiers") == "VIANDE_BLANCHE"
    assert classify_ciqual(_MEAT_FISH, "lapin") == "VIANDE_BLANCHE"


# ============================================================================
# classify_ciqual — groupe vegetal (legume / fruit / legumineuse / noix / feculent)
# ============================================================================

def test_classify_legume():
    assert classify_ciqual(_PLANTS, "légumes") == "LEGUME"
    assert classify_ciqual(_PLANTS, "champignons") == "LEGUME"
    assert classify_ciqual(_PLANTS, "algues") == "LEGUME"


def test_classify_fruit():
    assert classify_ciqual(_PLANTS, "fruits") == "FRUIT"


def test_classify_legumineuse():
    assert classify_ciqual(_PLANTS, "légumineuses") == "LEGUMINEUSE"
    assert classify_ciqual(_PLANTS, "légumes secs") == "LEGUMINEUSE"


def test_classify_noix_graine():
    assert classify_ciqual(_PLANTS, "fruits à coque et graines oléagineuses") == "NOIX_GRAINE"


def test_classify_pomme_de_terre_est_feculent():
    assert classify_ciqual(_PLANTS, "pommes de terre et autres tubercules") == "CEREALE_FECULENT"


# ============================================================================
# classify_ciqual — autres groupes au niveau groupe
# ============================================================================

def test_classify_groupes_simples():
    assert classify_ciqual("produits laitiers et assimilés", "fromages") == "LAITAGE"
    assert classify_ciqual("produits céréaliers", "pâtes, riz et céréales") == "CEREALE_FECULENT"
    assert classify_ciqual("produits sucrés", "chocolats") == "PRODUIT_SUCRE"
    assert classify_ciqual("glaces et sorbets", "glaces") == "PRODUIT_SUCRE"
    assert classify_ciqual("matières grasses", "huiles") == "MATIERE_GRASSE"
    assert classify_ciqual("eaux", "eaux") == "BOISSON"
    assert classify_ciqual("boissons", "sodas") == "BOISSON"
    assert classify_ciqual("entrées et plats composés", "pizzas") == "PLAT_COMPOSE"


def test_classify_fallback_autre():
    assert classify_ciqual("aides culinaires et ingrédients divers", "sauces") == "AUTRE"
    assert classify_ciqual("aliments infantiles", "petits pots") == "AUTRE"
    assert classify_ciqual("", "") == "AUTRE"
    assert classify_ciqual(None, None) == "AUTRE"


def test_classify_insensible_casse_et_accents():
    # Le matching est normalise (minuscule + accents retires).
    assert classify_ciqual("VIANDES, ŒUFS, POISSONS ET ASSIMILÉS", "POISSONS CRUS") == "POISSON"
    assert classify_ciqual("Produits Laitiers", "Yaourts") == "LAITAGE"


# ============================================================================
# realm_of — derivation groupe -> regne
# ============================================================================

def test_realm_animale():
    for g in ("VIANDE_ROUGE", "VIANDE_BLANCHE", "POISSON", "FRUITS_DE_MER", "OEUF", "LAITAGE"):
        assert realm_of(g) == REALM_ANIMALE


def test_realm_vegetale():
    for g in ("LEGUMINEUSE", "LEGUME", "FRUIT", "CEREALE_FECULENT", "NOIX_GRAINE"):
        assert realm_of(g) == REALM_VEGETALE


def test_realm_complement():
    assert realm_of("COMPLEMENT_MACRO") == REALM_COMPLEMENT
    assert realm_of("COMPLEMENT_MICRO") == REALM_COMPLEMENT


def test_realm_autre():
    for g in ("MATIERE_GRASSE", "PRODUIT_SUCRE", "BOISSON", "PLAT_COMPOSE", "AUTRE"):
        assert realm_of(g) == REALM_AUTRE


def test_realm_none_et_inconnu():
    assert realm_of(None) == REALM_AUTRE
    assert realm_of("") == REALM_AUTRE
    assert realm_of("PAS_UN_GROUPE") == REALM_AUTRE


def test_classify_ciqual_returns_only_known_groups():
    # Tout retour de classify_ciqual doit etre un groupe curate connu.
    samples = [
        (_MEAT_FISH, "viandes crues"),
        (_MEAT_FISH, "volailles"),
        (_PLANTS, "fruits"),
        ("produits sucrés", "bonbons"),
        ("inconnu", "inconnu"),
    ]
    for grp, ssgrp in samples:
        assert classify_ciqual(grp, ssgrp) in FOOD_GROUPS


def test_every_group_has_a_realm():
    # Coherence : chaque groupe curate a un regne defini (pas de fallback masque).
    realms = {REALM_ANIMALE, REALM_VEGETALE, REALM_COMPLEMENT, REALM_AUTRE}
    for g in FOOD_GROUPS:
        assert realm_of(g) in realms
