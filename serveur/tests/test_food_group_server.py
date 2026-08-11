"""Tests serveur de la vague S1 [Categories d'aliments] : cablage de la colonne
`food_group` cote DB/API/import (au-dela du module pur food_taxonomy + parser
CIQUAL, deja couverts par test_food_taxonomy.py / test_nutrition_v2.py).

Comportements observables couverts ici :
- API round-trip : `foodGroup` (alias camelCase) traverse PUT/GET (modele Food +
  schema FoodBase) et vaut null par defaut quand omis.
- starter_pack.copy_nutrition_pack : le food_group du catalogue template est
  recopie tel quel au nouveau user (signup / backfill par user).
- scripts/backfill_nutrition.py --food-group : propage le food_group du template
  vers les foods CIQUAL des users par source_ref, en bumpant updated_at, et est
  idempotent (IS DISTINCT FROM : 2e run = aucun bump).
"""
import importlib
import uuid as uuidlib

import bcrypt
from sqlalchemy import select
from sqlalchemy.ext.asyncio import async_sessionmaker

from app.models import User
from app.models.food import Food
from app.settings import settings
from app.starter_pack import copy_nutrition_pack

from .conftest import login_headers

backfill = importlib.import_module("scripts.backfill_nutrition")


# ============================================================================
# API round-trip — colonne food_group (modele) + alias foodGroup (schema)
# ============================================================================

def _food_payload(u: str, **overrides) -> dict:
    payload = {
        "uuid": u,
        "name": "Poulet rôti",
        "source": "CIQUAL",
        "kcalPer100g": 200.0,
        "proteinPer100g": 27.0,
        "carbsPer100g": 0.0,
        "fatPer100g": 10.0,
    }
    payload.update(overrides)
    return payload


async def test_food_group_round_trips_through_api(client):
    """PUT avec foodGroup -> persiste -> ressort sur GET (unitaire + liste)."""
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuidlib.uuid4())

    r = await client.put(
        f"/api/v1/foods/{u}",
        json=_food_payload(u, foodGroup="VIANDE_BLANCHE"),
        headers=headers,
    )
    assert r.status_code == 200, r.text
    assert r.json()["foodGroup"] == "VIANDE_BLANCHE"

    r2 = await client.get(f"/api/v1/foods/{u}", headers=headers)
    assert r2.status_code == 200, r2.text
    assert r2.json()["foodGroup"] == "VIANDE_BLANCHE"

    listed = {f["uuid"]: f for f in (await client.get("/api/v1/foods", headers=headers)).json()}
    assert listed[u]["foodGroup"] == "VIANDE_BLANCHE"


async def test_food_group_defaults_to_null_when_omitted(client):
    """Colonne nullable : un food cree sans foodGroup ressort foodGroup=null."""
    headers = await login_headers(client, "testuser", "testpass")
    u = str(uuidlib.uuid4())
    r = await client.put(f"/api/v1/foods/{u}", json=_food_payload(u), headers=headers)
    assert r.status_code == 200, r.text
    assert r.json()["foodGroup"] is None


# ============================================================================
# Helpers DB pour les tests de copie / backfill
# ============================================================================

async def _get_or_create_template(session) -> User:
    """starter_template : get-or-create (meme convention que test_nutrition_v2,
    on ne supprime jamais le template, seulement les foods de test ajoutes)."""
    template = (await session.execute(
        select(User).where(User.username == settings.STARTER_TEMPLATE_USERNAME)
    )).scalar_one_or_none()
    if template is None:
        pw_hash = bcrypt.hashpw(b"tpl", bcrypt.gensalt()).decode("utf-8")
        template = User(username=settings.STARTER_TEMPLATE_USERNAME, hashed_password=pw_hash)
        session.add(template)
        await session.flush()
    return template


def _ciqual_food(**kw) -> Food:
    """Food CIQUAL minimal valide (macros NOT NULL)."""
    base = dict(
        kcal_per_100g=200.0, protein_per_100g=27.0,
        carbs_per_100g=0.0, fat_per_100g=10.0,
        source="CIQUAL", is_favorite=False, archived=False,
    )
    base.update(kw)
    return Food(**base)


# ============================================================================
# starter_pack.copy_nutrition_pack — recopie le food_group au nouveau user
# ============================================================================

async def test_copy_nutrition_pack_copies_food_group(test_engine):
    session_maker = async_sessionmaker(test_engine, expire_on_commit=False)
    ref = "fg_copy_" + uuidlib.uuid4().hex[:8]
    tpl_food_uuid = str(uuidlib.uuid4())
    username = "fg_copy_user"
    user_id = None
    try:
        async with session_maker() as s:
            template = await _get_or_create_template(s)
            s.add(_ciqual_food(
                uuid=tpl_food_uuid, user_id=template.id, name="Poulet rôti",
                source_ref=ref, food_group="VIANDE_BLANCHE",
            ))
            pw_hash = bcrypt.hashpw(b"x", bcrypt.gensalt()).decode("utf-8")
            user = User(username=username, hashed_password=pw_hash)
            s.add(user)
            await s.flush()
            user_id = user.id
            await copy_nutrition_pack(s, user_id)
            await s.commit()

        async with session_maker() as s:
            copied = (await s.execute(
                select(Food).where(Food.user_id == user_id, Food.source_ref == ref)
            )).scalar_one_or_none()
            assert copied is not None
            assert copied.uuid != tpl_food_uuid          # nouvel uuid (remap)
            assert copied.food_group == "VIANDE_BLANCHE"  # food_group recopie tel quel
    finally:
        async with session_maker() as s:
            obj = (await s.execute(
                select(User).where(User.username == username)
            )).scalar_one_or_none()
            if obj is not None:
                await s.delete(obj)  # cascade foods/portions/presets du user
            tpl = (await s.execute(
                select(Food).where(Food.uuid == tpl_food_uuid)
            )).scalar_one_or_none()
            if tpl is not None:
                await s.delete(tpl)
            await s.commit()


# ============================================================================
# scripts/backfill_nutrition.py --food-group — propagation + idempotence
# ============================================================================

async def test_backfill_food_group_propagates_by_source_ref_and_is_idempotent(test_engine):
    session_maker = async_sessionmaker(test_engine, expire_on_commit=False)
    ref = "fg_bf_" + uuidlib.uuid4().hex[:8]
    tpl_food_uuid = str(uuidlib.uuid4())
    user_food_uuid = str(uuidlib.uuid4())
    username = "fg_backfill_user"
    try:
        async with session_maker() as s:
            template = await _get_or_create_template(s)
            # Template : food CIQUAL AVEC food_group (pose par le re-import).
            s.add(_ciqual_food(
                uuid=tpl_food_uuid, user_id=template.id, name="Saumon",
                source_ref=ref, food_group="POISSON",
            ))
            # User existant : meme source_ref mais food_group encore NULL.
            pw_hash = bcrypt.hashpw(b"x", bcrypt.gensalt()).decode("utf-8")
            user = User(username=username, hashed_password=pw_hash)
            s.add(user)
            await s.flush()
            s.add(_ciqual_food(
                uuid=user_food_uuid, user_id=user.id, name="Saumon",
                source_ref=ref, food_group=None, updated_at=None,
            ))
            await s.commit()

        # 1er run : pose le food_group + bump updated_at.
        async with session_maker() as s:
            await backfill._backfill_food_group(s)  # commit interne

        async with session_maker() as s:
            food = (await s.execute(
                select(Food).where(Food.uuid == user_food_uuid)
            )).scalar_one()
            assert food.food_group == "POISSON"
            assert food.updated_at is not None
            first_updated = food.updated_at

        # 2e run : IS DISTINCT FROM -> aucune row touchee, updated_at fige.
        async with session_maker() as s:
            await backfill._backfill_food_group(s)

        async with session_maker() as s:
            food = (await s.execute(
                select(Food).where(Food.uuid == user_food_uuid)
            )).scalar_one()
            assert food.updated_at == first_updated  # idempotent : pas de re-bump
    finally:
        async with session_maker() as s:
            obj = (await s.execute(
                select(User).where(User.username == username)
            )).scalar_one_or_none()
            if obj is not None:
                await s.delete(obj)
            tpl = (await s.execute(
                select(Food).where(Food.uuid == tpl_food_uuid)
            )).scalar_one_or_none()
            if tpl is not None:
                await s.delete(tpl)
            await s.commit()
