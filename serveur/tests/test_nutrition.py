"""Tests Nutrition V1 — 8 entités (cf. docs/NUTRITION_DESIGN.md).

Couvre :
- CRUD canonique : PUT (create + update), GET all, GET by uuid, DELETE, bulk
- ownership cross-user : 403 sur upsert d'un uuid/parent d'autrui, 404 sur GET/DELETE
- cascade ownership enfants : food_portions -> foods, recipe_ingredients -> recipes,
  meal_entries -> meals (+ refs food/recipe optionnelles)
- snapshot D5 : suppression du Food source -> l'entry survit avec foodUUID NULL
"""
import uuid as uuidlib

from .conftest import login_headers


def _food_payload(u: str, **overrides) -> dict:
    payload = {
        "uuid": u,
        "name": "Oeuf",
        "source": "CUSTOM",
        "kcalPer100g": 145.0,
        "proteinPer100g": 12.5,
        "carbsPer100g": 1.1,
        "fatPer100g": 10.0,
    }
    payload.update(overrides)
    return payload


def _entry_payload(u: str, meal_uuid: str, **overrides) -> dict:
    payload = {
        "uuid": u,
        "mealUUID": meal_uuid,
        "displayName": "Oeuf",
        "quantityG": 120.0,
        "kcalPer100g": 145.0,
        "proteinPer100g": 12.5,
        "carbsPer100g": 1.1,
        "fatPer100g": 10.0,
    }
    payload.update(overrides)
    return payload


# -------------------- foods --------------------

async def test_put_food_create_and_get(client):
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuidlib.uuid4())
    r = await client.put(f"/api/v1/foods/{u}", json=_food_payload(u), headers=headers)
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["uuid"] == u
    assert data["source"] == "CUSTOM"
    assert data["kcalPer100g"] == 145.0
    assert data["isFavorite"] is False
    assert data["archived"] is False
    assert "userId" in data

    r2 = await client.get(f"/api/v1/foods/{u}", headers=headers)
    assert r2.status_code == 200, r2.text
    assert r2.json()["name"] == "Oeuf"

    r3 = await client.get("/api/v1/foods", headers=headers)
    assert u in [f["uuid"] for f in r3.json()]
    # Hydratation (2026-07-05) : isWater absent du payload → défaut False.
    assert data["isWater"] is False


async def test_put_food_is_water_round_trip(client):
    """Hydratation : le flag isWater (boisson eau) est persisté et relu."""
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuidlib.uuid4())
    r = await client.put(
        f"/api/v1/foods/{u}",
        json=_food_payload(u, name="Wattwiller", source="OFF", isWater=True),
        headers=headers,
    )
    assert r.status_code == 200, r.text
    assert r.json()["isWater"] is True

    g = await client.get(f"/api/v1/foods/{u}", headers=headers)
    assert g.status_code == 200, g.text
    assert g.json()["isWater"] is True


async def test_put_food_uuid_mismatch_returns_400(client):
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuidlib.uuid4())
    r = await client.put(
        f"/api/v1/foods/{u}",
        json=_food_payload(str(uuidlib.uuid4())),
        headers=headers,
    )
    assert r.status_code == 400, r.text


async def test_bulk_upsert_foods(client):
    headers = await login_headers(client, "testuser", "testpass")
    u1, u2 = str(uuidlib.uuid4()), str(uuidlib.uuid4())
    r = await client.put(
        "/api/v1/foods/bulk",
        json=[_food_payload(u1), _food_payload(u2, name="Riz", source="CIQUAL")],
        headers=headers,
    )
    assert r.status_code == 200, r.text
    assert {u1, u2}.issubset({f["uuid"] for f in r.json()})


async def test_food_cross_user_forbidden(client):
    headers_a = await login_headers(client, "testuser", "testpass")
    headers_b = await login_headers(client, "otheruser", "otherpass")
    u = str(uuidlib.uuid4())
    assert (await client.put(f"/api/v1/foods/{u}", json=_food_payload(u), headers=headers_a)).status_code == 200

    # B tente d'ecraser le meme uuid -> 403
    r = await client.put(f"/api/v1/foods/{u}", json=_food_payload(u, name="Hijack"), headers=headers_b)
    assert r.status_code == 403, r.text
    # GET/DELETE d'autrui -> 404
    assert (await client.get(f"/api/v1/foods/{u}", headers=headers_b)).status_code == 404
    assert (await client.delete(f"/api/v1/foods/{u}", headers=headers_b)).status_code == 404
    # Toujours la pour son proprietaire
    assert (await client.get(f"/api/v1/foods/{u}", headers=headers_a)).status_code == 200


async def test_delete_food(client):
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuidlib.uuid4())
    await client.put(f"/api/v1/foods/{u}", json=_food_payload(u), headers=headers)
    assert (await client.delete(f"/api/v1/foods/{u}", headers=headers)).status_code == 200
    assert (await client.get(f"/api/v1/foods/{u}", headers=headers)).status_code == 404


# -------------------- food_portions --------------------

async def test_food_portion_crud_and_cascade_ownership(client):
    headers_a = await login_headers(client, "testuser", "testpass")
    headers_b = await login_headers(client, "otheruser", "otherpass")
    fu = str(uuidlib.uuid4())
    await client.put(f"/api/v1/foods/{fu}", json=_food_payload(fu), headers=headers_a)

    pu = str(uuidlib.uuid4())
    r = await client.put(
        f"/api/v1/food-portions/{pu}",
        json={"uuid": pu, "foodUUID": fu, "label": "1 oeuf", "grams": 60.0},
        headers=headers_a,
    )
    assert r.status_code == 200, r.text
    assert r.json()["grams"] == 60.0

    # GET all joint sur le parent -> visible pour A, pas pour B
    assert pu in [p["uuid"] for p in (await client.get("/api/v1/food-portions", headers=headers_a)).json()]
    assert pu not in [p["uuid"] for p in (await client.get("/api/v1/food-portions", headers=headers_b)).json()]

    # B ne peut pas creer une portion sous le food de A -> 403
    pu_b = str(uuidlib.uuid4())
    r2 = await client.put(
        f"/api/v1/food-portions/{pu_b}",
        json={"uuid": pu_b, "foodUUID": fu, "label": "hack", "grams": 1.0},
        headers=headers_b,
    )
    assert r2.status_code == 403, r2.text

    # GET/DELETE cross-user -> 404
    assert (await client.get(f"/api/v1/food-portions/{pu}", headers=headers_b)).status_code == 404
    assert (await client.delete(f"/api/v1/food-portions/{pu}", headers=headers_b)).status_code == 404
    # DELETE par le proprietaire OK
    assert (await client.delete(f"/api/v1/food-portions/{pu}", headers=headers_a)).status_code == 200


# -------------------- recipes + recipe_ingredients --------------------

async def test_recipe_and_ingredient_crud(client):
    headers = await login_headers(client, "testuser", "testpass")
    fu = str(uuidlib.uuid4())
    await client.put(f"/api/v1/foods/{fu}", json=_food_payload(fu, name="Avoine"), headers=headers)

    ru = str(uuidlib.uuid4())
    r = await client.put(
        f"/api/v1/recipes/{ru}",
        json={"uuid": ru, "name": "Bol d'avoine", "kind": "RECIPE", "totalWeightG": 350.0},
        headers=headers,
    )
    assert r.status_code == 200, r.text
    assert r.json()["kind"] == "RECIPE"

    iu = str(uuidlib.uuid4())
    r2 = await client.put(
        f"/api/v1/recipe-ingredients/{iu}",
        json={"uuid": iu, "recipeUUID": ru, "foodUUID": fu, "quantityG": 80.0, "orderIndex": 0},
        headers=headers,
    )
    assert r2.status_code == 200, r2.text
    assert r2.json()["orderIndex"] == 0

    assert iu in [i["uuid"] for i in (await client.get("/api/v1/recipe-ingredients", headers=headers)).json()]


async def test_recipe_ingredient_cross_user_forbidden(client):
    headers_a = await login_headers(client, "testuser", "testpass")
    headers_b = await login_headers(client, "otheruser", "otherpass")
    fu, ru = str(uuidlib.uuid4()), str(uuidlib.uuid4())
    await client.put(f"/api/v1/foods/{fu}", json=_food_payload(fu), headers=headers_a)
    await client.put(
        f"/api/v1/recipes/{ru}",
        json={"uuid": ru, "name": "Privee", "kind": "SAVED_MEAL"},
        headers=headers_a,
    )

    # B ne peut pas ajouter un ingredient dans la recette de A
    iu = str(uuidlib.uuid4())
    r = await client.put(
        f"/api/v1/recipe-ingredients/{iu}",
        json={"uuid": iu, "recipeUUID": ru, "foodUUID": fu, "quantityG": 10.0, "orderIndex": 0},
        headers=headers_b,
    )
    assert r.status_code == 403, r.text

    # GET recette d'autrui -> 404
    assert (await client.get(f"/api/v1/recipes/{ru}", headers=headers_b)).status_code == 404


# -------------------- meal_presets --------------------

async def test_meal_preset_crud(client):
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuidlib.uuid4())
    r = await client.put(
        f"/api/v1/meal-presets/{u}",
        json={"uuid": u, "name": "Petit-dej", "orderIndex": 0, "defaultTime": "07:30"},
        headers=headers,
    )
    assert r.status_code == 200, r.text
    assert r.json()["defaultTime"] == "07:30"

    assert u in [p["uuid"] for p in (await client.get("/api/v1/meal-presets", headers=headers)).json()]
    assert (await client.delete(f"/api/v1/meal-presets/{u}", headers=headers)).status_code == 200


# -------------------- meals + meal_entries --------------------

async def test_meal_and_entry_crud_with_snapshot(client):
    headers = await login_headers(client, "testuser", "testpass")
    mu = str(uuidlib.uuid4())
    r = await client.put(
        f"/api/v1/meals/{mu}",
        json={"uuid": mu, "date": "2026-06-12", "name": "Petit-dej", "orderIndex": 0},
        headers=headers,
    )
    assert r.status_code == 200, r.text

    fu = str(uuidlib.uuid4())
    await client.put(f"/api/v1/foods/{fu}", json=_food_payload(fu), headers=headers)

    eu = str(uuidlib.uuid4())
    r2 = await client.put(
        f"/api/v1/meal-entries/{eu}",
        json=_entry_payload(eu, mu, foodUUID=fu, portionLabel="2 oeufs"),
        headers=headers,
    )
    assert r2.status_code == 200, r2.text
    data = r2.json()
    assert data["foodUUID"] == fu
    assert data["displayName"] == "Oeuf"
    assert data["kcalPer100g"] == 145.0

    # Snapshot D5 : suppression du Food source -> l'entry survit, foodUUID passe NULL
    assert (await client.delete(f"/api/v1/foods/{fu}", headers=headers)).status_code == 200
    r3 = await client.get(f"/api/v1/meal-entries/{eu}", headers=headers)
    assert r3.status_code == 200, r3.text
    surviving = r3.json()
    assert surviving["foodUUID"] is None
    assert surviving["displayName"] == "Oeuf"
    assert surviving["kcalPer100g"] == 145.0


async def test_meal_entry_cross_user_forbidden(client):
    headers_a = await login_headers(client, "testuser", "testpass")
    headers_b = await login_headers(client, "otheruser", "otherpass")
    mu = str(uuidlib.uuid4())
    await client.put(
        f"/api/v1/meals/{mu}",
        json={"uuid": mu, "date": "2026-06-12", "name": "Dejeuner", "orderIndex": 1},
        headers=headers_a,
    )

    # B ne peut pas ajouter une entry dans le meal de A -> 403
    eu = str(uuidlib.uuid4())
    r = await client.put(
        f"/api/v1/meal-entries/{eu}",
        json=_entry_payload(eu, mu),
        headers=headers_b,
    )
    assert r.status_code == 403, r.text

    # Entry de A invisible pour B
    eu_a = str(uuidlib.uuid4())
    await client.put(f"/api/v1/meal-entries/{eu_a}", json=_entry_payload(eu_a, mu), headers=headers_a)
    assert (await client.get(f"/api/v1/meal-entries/{eu_a}", headers=headers_b)).status_code == 404
    assert (await client.delete(f"/api/v1/meal-entries/{eu_a}", headers=headers_b)).status_code == 404

    # Meal d'autrui : upsert -> 403, GET -> 404
    r2 = await client.put(
        f"/api/v1/meals/{mu}",
        json={"uuid": mu, "date": "2026-06-12", "name": "Hijack", "orderIndex": 9},
        headers=headers_b,
    )
    assert r2.status_code == 403, r2.text
    assert (await client.get(f"/api/v1/meals/{mu}", headers=headers_b)).status_code == 404


async def test_delete_meal_cascades_entries(client):
    headers = await login_headers(client, "testuser", "testpass")
    mu = str(uuidlib.uuid4())
    await client.put(
        f"/api/v1/meals/{mu}",
        json={"uuid": mu, "date": "2026-06-13", "name": "Diner", "orderIndex": 2},
        headers=headers,
    )
    eu = str(uuidlib.uuid4())
    await client.put(f"/api/v1/meal-entries/{eu}", json=_entry_payload(eu, mu), headers=headers)

    assert (await client.delete(f"/api/v1/meals/{mu}", headers=headers)).status_code == 200
    # CASCADE : l'entry est partie avec le meal
    assert (await client.get(f"/api/v1/meal-entries/{eu}", headers=headers)).status_code == 404


async def test_meal_preset_uuid_link_survives_rename_and_preset_delete(client):
    """Lien stable repas↔periode : presetUuid round-trip + SET NULL a la suppression du preset."""
    headers = await login_headers(client, "testuser", "testpass")

    pu = str(uuidlib.uuid4())
    await client.put(
        f"/api/v1/meal-presets/{pu}",
        json={"uuid": pu, "name": "Dejeuner", "orderIndex": 1},
        headers=headers,
    )

    mu = str(uuidlib.uuid4())
    r = await client.put(
        f"/api/v1/meals/{mu}",
        json={"uuid": mu, "date": "2026-06-12", "name": "Dejeuner", "orderIndex": 1, "presetUuid": pu},
        headers=headers,
    )
    assert r.status_code == 200, r.text
    assert r.json()["presetUuid"] == pu

    # Renommer le preset ne touche pas le lien (uuid stable).
    assert (
        await client.put(
            f"/api/v1/meal-presets/{pu}",
            json={"uuid": pu, "name": "Dejeuner4", "orderIndex": 1},
            headers=headers,
        )
    ).status_code == 200
    assert (await client.get(f"/api/v1/meals/{mu}", headers=headers)).json()["presetUuid"] == pu

    # Supprimer le preset : SET NULL -> le repas survit, presetUuid passe a null (ad hoc).
    assert (await client.delete(f"/api/v1/meal-presets/{pu}", headers=headers)).status_code == 200
    surviving = await client.get(f"/api/v1/meals/{mu}", headers=headers)
    assert surviving.status_code == 200, surviving.text
    assert surviving.json()["presetUuid"] is None


# -------------------- nutrition_goals --------------------

async def test_nutrition_goal_crud(client):
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuidlib.uuid4())
    r = await client.put(
        f"/api/v1/nutrition-goals/{u}",
        json={
            "uuid": u,
            "effectiveFrom": "2026-06-01",
            "kcal": 2500.0,
            "proteinG": 180.0,
            "carbsG": 280.0,
            "fatG": 70.0,
        },
        headers=headers,
    )
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["dayKind"] == "ALL"          # default v1 (politique 10)
    assert data["proteinG"] == 180.0

    assert u in [g["uuid"] for g in (await client.get("/api/v1/nutrition-goals", headers=headers)).json()]
    assert (await client.delete(f"/api/v1/nutrition-goals/{u}", headers=headers)).status_code == 200


async def test_nutrition_goal_cross_user_forbidden(client):
    headers_a = await login_headers(client, "testuser", "testpass")
    headers_b = await login_headers(client, "otheruser", "otherpass")
    u = str(uuidlib.uuid4())
    payload = {
        "uuid": u,
        "effectiveFrom": "2026-06-01",
        "kcal": 2000.0,
        "proteinG": 150.0,
        "carbsG": 200.0,
        "fatG": 60.0,
    }
    assert (await client.put(f"/api/v1/nutrition-goals/{u}", json=payload, headers=headers_a)).status_code == 200
    assert (await client.put(f"/api/v1/nutrition-goals/{u}", json=payload, headers=headers_b)).status_code == 403
    assert (await client.get(f"/api/v1/nutrition-goals/{u}", headers=headers_b)).status_code == 404
