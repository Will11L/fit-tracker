"""Tests Nutrition V2 — seed CIQUAL + presets par defaut au signup + proxy OFF.

Couvre :
- copy_starter_pack etendu : foods (+ portions) du starter_template copies au
  /signup avec nouveaux uuids, + 4 meal_presets par defaut crees.
- scripts/import_ciqual.py : parsing pur (decimales virgule, traces, '< x',
  valeurs manquantes, resolution de colonnes kJ vs kcal, skip rows sans kcal).
- proxy OFF : normalisation per-100g (kcal direct + fallback kJ), 404 produit
  inconnu, 400 barcode invalide, cache TTL (pas de 2e appel sortant), 401 sans
  auth.
"""
import importlib
import uuid as uuidlib

import bcrypt
import pytest_asyncio
from sqlalchemy import select
from sqlalchemy.ext.asyncio import async_sessionmaker

from app.models import User
from app.models.food import Food
from app.models.food_portion import FoodPortion
from app.settings import settings

from .conftest import login_headers

off_mod = importlib.import_module("app.routers.nutrition_off_router")
ciqual = importlib.import_module("scripts.import_ciqual")


# ============================================================================
# copy_starter_pack etendu (foods + portions + meal presets)
# ============================================================================

_TPL_FOOD_REFS = ("test_ciqual_T1", "test_ciqual_T2")


@pytest_asyncio.fixture
async def nutrition_signup_env(test_engine):
    """Starter_template + 2 foods CIQUAL (dont 1 avec portion) sur le template.
    Yield un tracker de usernames crees ; teardown : users jetables + foods
    template de test supprimes (la DB de test est session-scoped)."""
    session_maker = async_sessionmaker(test_engine, expire_on_commit=False)
    created_usernames: list[str] = []
    template_food_uuids: list[str] = []

    async with session_maker() as session:
        template = (await session.execute(
            select(User).where(User.username == settings.STARTER_TEMPLATE_USERNAME)
        )).scalar_one_or_none()
        if template is None:
            pw_hash = bcrypt.hashpw(b"tpl", bcrypt.gensalt()).decode("utf-8")
            template = User(
                username=settings.STARTER_TEMPLATE_USERNAME, hashed_password=pw_hash,
            )
            session.add(template)
            await session.flush()

        f1_uuid, f2_uuid = str(uuidlib.uuid4()), str(uuidlib.uuid4())
        template_food_uuids.extend([f1_uuid, f2_uuid])
        session.add(Food(
            uuid=f1_uuid, user_id=template.id, name="Oeuf, cru",
            source="CIQUAL", source_ref=_TPL_FOOD_REFS[0],
            kcal_per_100g=140.0, protein_per_100g=12.7,
            carbs_per_100g=0.27, fat_per_100g=9.83,
            fiber_per_100g=0.0, is_favorite=False, archived=False,
        ))
        session.add(Food(
            uuid=f2_uuid, user_id=template.id, name="Riz blanc, cuit",
            source="CIQUAL", source_ref=_TPL_FOOD_REFS[1],
            kcal_per_100g=135.0, protein_per_100g=2.9,
            carbs_per_100g=28.8, fat_per_100g=0.65,
            is_favorite=False, archived=False,
        ))
        await session.flush()
        session.add(FoodPortion(
            uuid=str(uuidlib.uuid4()), food_uuid=f1_uuid,
            label="1 oeuf", grams=60.0,
        ))
        await session.commit()

    def _track(username: str) -> None:
        created_usernames.append(username)

    yield _track, template_food_uuids

    async with session_maker() as session:
        for username in created_usernames:
            obj = (await session.execute(
                select(User).where(User.username == username)
            )).scalar_one_or_none()
            if obj is not None:
                await session.delete(obj)  # cascade : foods/portions/presets du user
        tpl_foods = (await session.execute(
            select(Food).where(Food.uuid.in_(template_food_uuids))
        )).scalars().all()
        for f in tpl_foods:
            await session.delete(f)  # cascade : portions
        await session.commit()


async def test_signup_copies_foods_and_creates_default_presets(client, nutrition_signup_env):
    track, template_food_uuids = nutrition_signup_env
    track("nutri_signup_user")
    resp = await client.post("/api/v1/signup", json={
        "username": "nutri_signup_user", "password": "supersecret",
    })
    assert resp.status_code == 201, resp.text
    headers = await login_headers(client, "nutri_signup_user", "supersecret")

    # Foods copies : memes donnees, NOUVEAUX uuids (remap).
    foods = (await client.get("/api/v1/foods", headers=headers)).json()
    by_ref = {f["sourceRef"]: f for f in foods if f["source"] == "CIQUAL"}
    assert set(_TPL_FOOD_REFS).issubset(by_ref.keys())
    egg = by_ref[_TPL_FOOD_REFS[0]]
    assert egg["name"] == "Oeuf, cru"
    assert egg["kcalPer100g"] == 140.0
    assert egg["uuid"] not in template_food_uuids

    # Portion du template copiee et rattachee au NOUVEAU food uuid.
    portions = (await client.get("/api/v1/food-portions", headers=headers)).json()
    egg_portions = [p for p in portions if p["foodUUID"] == egg["uuid"]]
    assert len(egg_portions) == 1
    assert egg_portions[0]["label"] == "1 oeuf"
    assert egg_portions[0]["grams"] == 60.0

    # 4 meal presets par defaut, dans l'ordre.
    presets = (await client.get("/api/v1/meal-presets", headers=headers)).json()
    assert len(presets) == 4
    ordered = sorted(presets, key=lambda p: p["orderIndex"])
    assert [p["name"] for p in ordered] == ["Petit-déj", "Déjeuner", "Dîner", "Collation"]
    assert ordered[0]["defaultTime"] == "07:30"


async def test_backfill_nutrition_existing_user_idempotent(test_engine, nutrition_signup_env):
    """copy_nutrition_pack (reutilise par scripts/backfill_nutrition.py) :
    un user existant (pre-V2, 0 food / 0 preset) recoit le catalogue + les
    presets ; re-run = aucun doublon et les renommages de presets sont
    preserves (gardes separees foods vs presets)."""
    from app.models.meal_preset import MealPreset
    from app.starter_pack import copy_nutrition_pack

    track, _ = nutrition_signup_env
    track("nutri_backfill_user")
    session_maker = async_sessionmaker(test_engine, expire_on_commit=False)

    async with session_maker() as session:
        pw_hash = bcrypt.hashpw(b"x", bcrypt.gensalt()).decode("utf-8")
        user = User(username="nutri_backfill_user", hashed_password=pw_hash)
        session.add(user)
        await session.flush()
        user_id = user.id

        await copy_nutrition_pack(session, user_id)
        await session.commit()

    async with session_maker() as session:
        foods = (await session.execute(
            select(Food).where(Food.user_id == user_id, Food.source == "CIQUAL")
        )).scalars().all()
        assert set(_TPL_FOOD_REFS).issubset({f.source_ref for f in foods})
        presets = (await session.execute(
            select(MealPreset).where(MealPreset.user_id == user_id)
        )).scalars().all()
        assert len(presets) == 4

        # Renommage user d'un preset, puis re-run du backfill.
        presets[0].name = "Brunch"
        await copy_nutrition_pack(session, user_id)
        await session.commit()

    async with session_maker() as session:
        refs = [f.source_ref for f in (await session.execute(
            select(Food).where(Food.user_id == user_id, Food.source == "CIQUAL")
        )).scalars().all()]
        assert len(refs) == len(set(refs))  # pas de doublon food
        presets = (await session.execute(
            select(MealPreset).where(MealPreset.user_id == user_id)
        )).scalars().all()
        assert len(presets) == 4  # pas de re-creation
        assert "Brunch" in {p.name for p in presets}  # renommage preserve


# ============================================================================
# scripts/import_ciqual.py — parsing pur
# ============================================================================

def test_parse_nutrient_french_conventions():
    assert ciqual.parse_nutrient("12,5") == 12.5
    assert ciqual.parse_nutrient("traces") == 0.0
    assert ciqual.parse_nutrient("< 0,5") == 0.5
    assert ciqual.parse_nutrient("-") is None
    assert ciqual.parse_nutrient("") is None
    assert ciqual.parse_nutrient(None) is None
    assert ciqual.parse_nutrient(42) == 42.0


def test_extract_food_specs_from_ciqual_headers():
    headers = [
        "alim_code", "alim_nom_fr",
        "Energie, Règlement UE N° 1169/2011 (kJ/100 g)",
        "Energie, Règlement UE N° 1169/2011 (kcal/100 g)",
        "Protéines, N x facteur de Jones (g/100 g)",
        "Glucides (g/100 g)", "Lipides (g/100 g)",
        "Fibres alimentaires (g/100 g)", "Sucres (g/100 g)",
        "AG saturés (g/100 g)", "Sel chlorure de sodium (g/100 g)",
        "Sélénium (µg/100 g)",
    ]
    rows = [
        ["20904", "Oeuf, cru", "586", "140", "12,7", "0,27", "9,83", "0", "0,27", "2,64", "0,31", "20"],
        ["9104", "Riz blanc, cuit", "564", "135", "2,9", "28,8", "0,65", "traces", "-", "0,17", "< 0,01", "5"],
        ["99999", "Sans kcal", "100", "-", "1", "1", "1", "-", "-", "-", "-", "-"],
    ]
    specs = ciqual.extract_food_specs(headers, rows)
    assert len(specs) == 2  # la row sans kcal est skippee

    egg = specs[0]
    assert egg["source_ref"] == "20904"
    assert egg["name"] == "Oeuf, cru"
    assert egg["kcal_per_100g"] == 140.0       # colonne kcal, PAS la kJ (586)
    assert egg["protein_per_100g"] == 12.7
    assert egg["carbs_per_100g"] == 0.27
    assert egg["fat_per_100g"] == 9.83
    assert egg["salt_per_100g"] == 0.31        # sel, PAS selenium (20)

    rice = specs[1]
    assert rice["fiber_per_100g"] == 0.0       # traces -> 0.0
    assert rice["sugar_per_100g"] is None      # '-' -> None
    assert rice["salt_per_100g"] == 0.01       # '< 0,01' -> borne haute


def test_extract_food_specs_fiber_not_confused_with_energy_jones_column():
    """Régression 2026-06-13 : la colonne 'Energie ... avec fibres (kJ)' contient
    le mot 'fibres' et précède 'Fibres alimentaires' — le mapping doit prendre la
    VRAIE colonne fibres (10,6), pas l'énergie kJ (1570)."""
    headers = [
        "alim_code", "alim_nom_fr",
        "Energie, Règlement UE N° 1169/2011 (kJ/100 g)",
        "Energie, Règlement UE N° 1169/2011 (kcal/100 g)",
        "Energie, N x facteur Jones, avec fibres  (kJ/100 g)",
        "Protéines, N x facteur de Jones (g/100 g)",
        "Glucides (g/100 g)", "Lipides (g/100 g)",
        "Fibres alimentaires (g/100 g)",
    ]
    rows = [["20102", "Avoine, crue", "1590", "378", "1570", "16,9", "55,7", "6,9", "10,6"]]
    specs = ciqual.extract_food_specs(headers, rows)
    assert specs[0]["kcal_per_100g"] == 378.0
    assert specs[0]["fiber_per_100g"] == 10.6  # PAS 1570 (énergie Jones kJ)


def test_extract_food_specs_populates_food_group():
    """Feature Categories d'aliments : le parser lit alim_grp_nom_fr /
    alim_ssgrp_nom_fr et derive un groupe curate via app.food_taxonomy."""
    headers = [
        "alim_code", "alim_nom_fr",
        "alim_grp_nom_fr", "alim_ssgrp_nom_fr",
        "Energie, Règlement UE N° 1169/2011 (kcal/100 g)",
        "Protéines, N x facteur de Jones (g/100 g)",
        "Glucides (g/100 g)", "Lipides (g/100 g)",
    ]
    rows = [
        ["20904", "Oeuf, cru", "viandes, œufs, poissons et assimilés",
         "œufs", "140", "12,7", "0,27", "9,83"],
        ["6010", "Poulet, rôti", "viandes, œufs, poissons et assimilés",
         "volailles et gibiers", "200", "27", "0", "10"],
        ["9104", "Riz blanc, cuit", "produits céréaliers",
         "pâtes, riz et céréales", "135", "2,9", "28,8", "0,3"],
    ]
    specs = ciqual.extract_food_specs(headers, rows)
    assert [s["food_group"] for s in specs] == ["OEUF", "VIANDE_BLANCHE", "CEREALE_FECULENT"]


def test_extract_food_specs_food_group_autre_when_columns_absent():
    """Anciens exports CIQUAL sans colonnes groupe/sous-groupe : food_group = AUTRE
    (classify_ciqual sur None/None), jamais une exception."""
    headers = [
        "alim_code", "alim_nom_fr",
        "Energie, Règlement UE N° 1169/2011 (kcal/100 g)",
        "Protéines, N x facteur de Jones (g/100 g)",
        "Glucides (g/100 g)", "Lipides (g/100 g)",
    ]
    rows = [["20904", "Oeuf, cru", "140", "12,7", "0,27", "9,83"]]
    specs = ciqual.extract_food_specs(headers, rows)
    assert specs[0]["food_group"] == "AUTRE"


def test_extract_food_specs_red_white_meat_via_third_ciqual_level():
    """Fix 2026-06-16 : la colonne 3e niveau CIQUAL (alim_ssssgrp_nom_fr) doit etre
    resolue ET passee a classify_ciqual pour trancher viande rouge/blanche.

    Le 2e niveau du CIQUAL 2025 reel est trop grossier ('viandes cuites/crues',
    sans mention de volaille) -> sans le 3e niveau, le poulet finit en VIANDE_ROUGE.
    Ce test verrouille le cablage du parser (mapping colonne + pass-through) : avec
    un ssgrp grossier, c'est le 3e niveau qui doit decider. Les regles rouge/blanche
    elles-memes sont testees dans test_food_taxonomy.py."""
    headers = [
        "alim_code", "alim_nom_fr",
        "alim_grp_nom_fr", "alim_ssgrp_nom_fr", "alim_ssssgrp_nom_fr",
        "Energie, Règlement UE N° 1169/2011 (kcal/100 g)",
        "Protéines, N x facteur de Jones (g/100 g)",
        "Glucides (g/100 g)", "Lipides (g/100 g)",
    ]
    rows = [
        # ssgrp grossier identique ("viandes cuites") : seul le 3e niveau differe.
        ["6010", "Poulet, rôti", "viandes, œufs, poissons et assimilés",
         "viandes cuites", "poulet", "200", "27", "0", "10"],
        ["21510", "Boeuf, steak, cuit", "viandes, œufs, poissons et assimilés",
         "viandes cuites", "boeuf et veau", "180", "26", "0", "8"],
    ]
    specs = ciqual.extract_food_specs(headers, rows)
    assert [s["food_group"] for s in specs] == ["VIANDE_BLANCHE", "VIANDE_ROUGE"]


def test_extract_food_specs_micros_and_vitamin_a_rae():
    """Vitamines & mineraux (pack essentiel ~10, D11 etendu) : matching strict
    des colonnes CIQUAL + vitamine A = RAE = retinol + beta-carotene/12.
    Pieges evites : 'fer' ne matche pas 'facteur de Jones', 'sodium' ne matche
    pas 'Sel chlorure de sodium' (en g)."""
    headers = [
        "alim_code", "alim_nom_fr",
        "Energie, Règlement UE N° 1169/2011 (kcal/100 g)",
        "Protéines, N x facteur de Jones (g/100 g)",
        "Glucides (g/100 g)", "Lipides (g/100 g)",
        "Sel chlorure de sodium (g/100 g)",
        "Calcium (mg/100 g)", "Fer (mg/100 g)", "Magnésium (mg/100 g)",
        "Potassium (mg/100 g)", "Sodium (mg/100 g)", "Zinc (mg/100 g)",
        "Rétinol (µg/100 g)", "Beta-Carotène (µg/100 g)",
        "Vitamine D (µg/100 g)", "Vitamine C (mg/100 g)", "Vitamine B12 (µg/100 g)",
    ]
    rows = [[
        "12345", "Aliment test",
        "100", "10", "5", "2",
        "0.5",                          # sel (g) -> ne doit PAS finir dans sodium
        "120", "1.9", "25", "350", "40", "1.1",   # mineraux (mg)
        "100", "600",                    # retinol 100µg + beta-carotene 600µg
        "1.5", "30", "0.8",              # vit D (µg), C (mg), B12 (µg)
    ]]
    specs = ciqual.extract_food_specs(headers, rows)
    s = specs[0]
    assert s["calcium_per_100g"] == 120.0
    assert s["iron_per_100g"] == 1.9          # 'fer', PAS 'facteur'
    assert s["magnesium_per_100g"] == 25.0
    assert s["potassium_per_100g"] == 350.0
    assert s["sodium_per_100g"] == 40.0       # 'Sodium (mg)', PAS le sel (0.5 g)
    assert s["zinc_per_100g"] == 1.1
    assert s["vitamin_d_per_100g"] == 1.5
    assert s["vitamin_c_per_100g"] == 30.0
    assert s["vitamin_b12_per_100g"] == 0.8
    # Vitamine A = RAE = 100 + 600/12 = 150 µg
    assert s["vitamin_a_per_100g"] == 150.0


def test_vitamin_a_rae_none_only_when_both_sources_missing():
    assert ciqual._vitamin_a_rae(None, None) is None
    assert ciqual._vitamin_a_rae(40.0, None) == 40.0       # beta-carotene absent -> 0
    assert ciqual._vitamin_a_rae(None, 24.0) == 2.0        # 24/12, retinol absent -> 0


def test_extract_food_specs_missing_required_columns_raises():
    try:
        ciqual.extract_food_specs(["foo", "bar"], [])
        assert False, "ValueError attendue"
    except ValueError:
        pass


# ============================================================================
# Proxy Open Food Facts
# ============================================================================

_OFF_SEARCH_PAYLOAD = {
    "products": [
        {
            "code": "3017620422003",
            "product_name": "Nutella",
            "brands": "Ferrero, Nutella",
            "serving_size": "15 g",
            "serving_quantity": "15",
            "nutriments": {
                "energy-kcal_100g": 539,
                "proteins_100g": 6.3,
                "carbohydrates_100g": 57.5,
                "fat_100g": 30.9,
                "sugars_100g": 56.3,
                "saturated-fat_100g": 10.6,
                "salt_100g": 0.107,
            },
        },
        # Sans kcal mais avec kJ -> fallback conversion
        {
            "code": "111",
            "product_name": "KJ Only",
            "nutriments": {"energy-kj_100g": 418.4, "proteins_100g": 5},
        },
        # Inexploitable (pas de nom) -> filtre
        {"code": "222", "product_name": "", "nutriments": {"energy-kcal_100g": 100}},
    ],
}


def _install_off_fake(monkeypatch, payload):
    calls = {"count": 0}

    async def fake_fetch_json(url, params=None):
        calls["count"] += 1
        return payload

    monkeypatch.setattr(off_mod, "_fetch_json", fake_fetch_json)
    off_mod._cache.clear()
    return calls


async def test_off_search_normalizes_per_100g(client, monkeypatch):
    _install_off_fake(monkeypatch, _OFF_SEARCH_PAYLOAD)
    headers = await login_headers(client, "testuser", "testpass")

    r = await client.get("/api/v1/nutrition/off/search", params={"q": "nutella"}, headers=headers)
    assert r.status_code == 200, r.text
    items = r.json()
    assert len(items) == 2  # le produit sans nom est filtre

    nutella = items[0]
    assert nutella["sourceRef"] == "3017620422003"
    assert nutella["name"] == "Nutella"
    assert nutella["brand"] == "Ferrero"           # 1ere marque seulement
    assert nutella["kcalPer100g"] == 539.0
    assert nutella["proteinPer100g"] == 6.3
    assert nutella["carbsPer100g"] == 57.5
    assert nutella["fatPer100g"] == 30.9
    assert nutella["sugarPer100g"] == 56.3
    assert nutella["satFatPer100g"] == 10.6
    assert nutella["saltPer100g"] == 0.107
    assert nutella["fiberPer100g"] is None
    assert nutella["servingSize"] == "15 g"
    assert nutella["servingQuantityG"] == 15.0

    kj_only = items[1]
    assert kj_only["kcalPer100g"] == 100.0         # 418.4 kJ / 4.184
    assert kj_only["carbsPer100g"] == 0.0          # macro NOT NULL manquante -> 0.0


async def test_off_normalizes_micros_grams_to_mg_ug(client, monkeypatch):
    """OFF stocke les `<nutriment>_100g` en grammes : le proxy convertit les
    mineraux + vit C en mg et les vit D / B12 / A en µg (pack essentiel ~10)."""
    _install_off_fake(monkeypatch, {
        "products": [{
            "code": "999",
            "product_name": "Micro test",
            "nutriments": {
                "energy-kcal_100g": 100,
                "iron_100g": 0.0019, "calcium_100g": 0.12, "sodium_100g": 0.04,
                "magnesium_100g": 0.025, "potassium_100g": 0.35, "zinc_100g": 0.0011,
                "vitamin-c_100g": 0.03,
                "vitamin-d_100g": 0.0000015, "vitamin-b12_100g": 0.0000008,
                "vitamin-a_100g": 0.00015,
            },
        }],
    })
    headers = await login_headers(client, "testuser", "testpass")
    r = await client.get("/api/v1/nutrition/off/search", params={"q": "micro"}, headers=headers)
    assert r.status_code == 200, r.text
    p = r.json()[0]
    assert p["ironPer100g"] == 1.9          # 0.0019 g -> mg
    assert p["calciumPer100g"] == 120.0
    assert p["sodiumPer100g"] == 40.0
    assert p["magnesiumPer100g"] == 25.0
    assert p["potassiumPer100g"] == 350.0
    assert p["zincPer100g"] == 1.1
    assert p["vitaminCPer100g"] == 30.0     # mg
    assert p["vitaminDPer100g"] == 1.5      # µg
    assert p["vitaminB12Per100g"] == 0.8    # µg
    assert p["vitaminAPer100g"] == 150.0    # µg


async def test_off_micros_absent_stay_none(client, monkeypatch):
    """Micros absents du produit OFF -> None (colonnes nullable, souvent partiel)."""
    _install_off_fake(monkeypatch, {
        "products": [{
            "code": "888", "product_name": "Sans micros",
            "nutriments": {"energy-kcal_100g": 50, "proteins_100g": 1},
        }],
    })
    headers = await login_headers(client, "testuser", "testpass")
    r = await client.get("/api/v1/nutrition/off/search", params={"q": "xy"}, headers=headers)
    p = r.json()[0]
    assert p["ironPer100g"] is None
    assert p["vitaminAPer100g"] is None


async def test_off_passes_through_categories_tags(client, monkeypatch):
    """Feature Categories d'aliments : les categories_tags OFF (slugs en:/fr:) sont relayees
    telles quelles ; le client les mappe vers un groupe curate (food-category.ts). Absent -> []."""
    _install_off_fake(monkeypatch, {
        "products": [
            {
                "code": "111", "product_name": "Whey",
                "categories_tags": ["en:dietary-supplements", "en:proteins"],
                "nutriments": {"energy-kcal_100g": 380, "proteins_100g": 75},
            },
            {
                "code": "222", "product_name": "Sans categories",
                "nutriments": {"energy-kcal_100g": 50},
            },
        ],
    })
    headers = await login_headers(client, "testuser", "testpass")
    r = await client.get("/api/v1/nutrition/off/search", params={"q": "whey"}, headers=headers)
    assert r.status_code == 200, r.text
    items = r.json()
    assert items[0]["categoriesTags"] == ["en:dietary-supplements", "en:proteins"]
    assert items[1]["categoriesTags"] == []  # absent -> liste vide


async def test_off_search_accepts_searchalicious_shape(client, monkeypatch):
    """L'API moderne (search.openfoodfacts.org) renvoie `hits` (pas `products`)
    et `brands` en tableau — les deux formats doivent etre normalises pareil."""
    _install_off_fake(monkeypatch, {
        "hits": [{
            "code": "3184670001110",
            "product_name": "Fromage blanc",
            "brands": ["Rians", "Autre"],
            "nutriments": {"energy-kcal_100g": 83, "proteins_100g": 4.5},
        }],
    })
    headers = await login_headers(client, "testuser", "testpass")
    r = await client.get("/api/v1/nutrition/off/search", params={"q": "fromage"}, headers=headers)
    assert r.status_code == 200, r.text
    items = r.json()
    assert len(items) == 1
    assert items[0]["brand"] == "Rians"  # 1ere marque du tableau
    assert items[0]["kcalPer100g"] == 83.0


async def test_off_search_uses_cache(client, monkeypatch):
    calls = _install_off_fake(monkeypatch, _OFF_SEARCH_PAYLOAD)
    headers = await login_headers(client, "testuser", "testpass")

    for _ in range(2):
        r = await client.get("/api/v1/nutrition/off/search", params={"q": "nutella"}, headers=headers)
        assert r.status_code == 200
    assert calls["count"] == 1  # 2e hit servi par le cache TTL


async def test_off_product_found_and_cached(client, monkeypatch):
    payload = {
        "status": 1,
        "code": "3017620422003",
        "product": dict(_OFF_SEARCH_PAYLOAD["products"][0]),
    }
    calls = _install_off_fake(monkeypatch, payload)
    headers = await login_headers(client, "testuser", "testpass")

    r = await client.get("/api/v1/nutrition/off/product/3017620422003", headers=headers)
    assert r.status_code == 200, r.text
    assert r.json()["sourceRef"] == "3017620422003"
    assert r.json()["kcalPer100g"] == 539.0

    await client.get("/api/v1/nutrition/off/product/3017620422003", headers=headers)
    assert calls["count"] == 1


async def test_off_product_not_found_returns_404(client, monkeypatch):
    _install_off_fake(monkeypatch, {"status": 0, "code": "404404"})
    headers = await login_headers(client, "testuser", "testpass")
    r = await client.get("/api/v1/nutrition/off/product/404404", headers=headers)
    assert r.status_code == 404, r.text


async def test_off_product_without_energy_imports_as_zero_kcal(client, monkeypatch):
    """Scan d'une eau minerale (aucun champ energie dans OFF) : le lookup par
    code-barres la garde en defaultant kcal a 0.0 (feedback scan 2026-07-05,
    Wattwiller rejete a tort en 404). Seul un produit sans nom/code reste rejete."""
    water = {
        "code": "3448781100002",
        "product_name": "Wattwiller",
        "brands": "Wattwiller",
        "nutriments": {},  # eau : aucun nutriment renseigne dans OFF
    }
    _install_off_fake(monkeypatch, {"status": 1, "code": "3448781100002", "product": water})
    headers = await login_headers(client, "testuser", "testpass")
    r = await client.get("/api/v1/nutrition/off/product/3448781100002", headers=headers)
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["sourceRef"] == "3448781100002"
    assert body["kcalPer100g"] == 0.0
    assert body["proteinPer100g"] == 0.0


async def test_off_search_still_filters_energyless_products(client, monkeypatch):
    """La recherche reste stricte : un produit sans energie est filtre (bruit),
    a la difference du lookup par code-barres (scan volontaire d'un produit precis)."""
    _install_off_fake(monkeypatch, {
        "products": [
            {"code": "1", "product_name": "Sans energie", "nutriments": {"proteins_100g": 0}},
            {"code": "2", "product_name": "Avec energie", "nutriments": {"energy-kcal_100g": 42}},
        ],
    })
    headers = await login_headers(client, "testuser", "testpass")
    r = await client.get("/api/v1/nutrition/off/search", params={"q": "eau"}, headers=headers)
    assert r.status_code == 200, r.text
    assert [p["sourceRef"] for p in r.json()] == ["2"]  # seul le produit avec energie remonte


async def test_off_product_invalid_barcode_returns_400(client, monkeypatch):
    calls = _install_off_fake(monkeypatch, {})
    headers = await login_headers(client, "testuser", "testpass")
    r = await client.get("/api/v1/nutrition/off/product/not-a-barcode", headers=headers)
    assert r.status_code == 400, r.text
    assert calls["count"] == 0  # rejete avant tout appel sortant


async def test_off_endpoints_require_auth(client):
    assert (await client.get("/api/v1/nutrition/off/search", params={"q": "riz"})).status_code == 401
    assert (await client.get("/api/v1/nutrition/off/product/123")).status_code == 401


async def test_fetch_json_retry_absorbs_transient_502(monkeypatch):
    """Le retry unique absorbe un 503 transitoire d'OFF (observe en prod
    2026-06-12 : cgi/search.pl renvoie des 503 sous charge -> 502 cote proxy)."""
    calls = {"n": 0}

    async def flaky(url, params=None):
        calls["n"] += 1
        if calls["n"] == 1:
            raise off_mod.HTTPException(status_code=502, detail="Open Food Facts injoignable")
        return {"ok": True}

    monkeypatch.setattr(off_mod, "_fetch_json", flaky)
    assert await off_mod._fetch_json_retry("http://x") == {"ok": True}
    assert calls["n"] == 2


async def test_fetch_json_retry_gives_up_after_second_failure(monkeypatch):
    calls = {"n": 0}

    async def always_down(url, params=None):
        calls["n"] += 1
        raise off_mod.HTTPException(status_code=502, detail="Open Food Facts injoignable")

    monkeypatch.setattr(off_mod, "_fetch_json", always_down)
    try:
        await off_mod._fetch_json_retry("http://x")
        assert False, "HTTPException attendue"
    except Exception as exc:
        assert getattr(exc, "status_code", None) == 502
    assert calls["n"] == 2
