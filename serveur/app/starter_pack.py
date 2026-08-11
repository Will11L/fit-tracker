"""Copy catalogue (muscles + exercises + relations) from the starter_template
user fixture to a newly created user at /signup time.

Nutrition V2 (2026-06-12, cf. docs/NUTRITION_DESIGN.md D9/D10) : copie aussi
le catalogue foods (seed CIQUAL via scripts/import_ciqual.py) + leurs
food_portions, et cree 4 meal_presets par defaut.

Cf. CLAUDE.md V8.4 + commit 8ae4b27.
"""
from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.exercise import Exercise
from app.models.exercise_muscle import ExerciseMuscle
from app.models.food import Food
from app.models.food_portion import FoodPortion
from app.models.meal_preset import MealPreset
from app.models.muscle import Muscle
from app.models.quote import Quote
from app.models.user import User
from app.settings import settings

# Presets de repas par defaut (D10, docs/NUTRITION_DESIGN.md §3.5). Definis ici
# en constante (pas dans le starter_template DB) : c'est une config par defaut
# fixe de l'app, pas du catalogue importable — renommables/supprimables par
# l'utilisateur ensuite. Noms FR assumes (audience de l'app), traites comme
# user-typed : jamais traduits (politique 18, cas special).
# Format : (name, order_index, default_time "HH:MM").
_DEFAULT_MEAL_PRESETS: list[tuple[str, int, str]] = [
    ("Petit-déj", 1, "07:30"),
    ("Déjeuner", 2, "12:30"),
    ("Dîner", 3, "19:30"),
    ("Collation", 4, "16:00"),
]


class StarterTemplateMissing(RuntimeError):
    """Le user fixture starter_template est introuvable en DB. Lance
    `python -m app.fill_database` pour le seeder."""


async def _get_starter_template_user_id(db: AsyncSession) -> int:
    res = await db.execute(
        select(User.id).where(User.username == settings.STARTER_TEMPLATE_USERNAME)
    )
    user_id = res.scalar_one_or_none()
    if user_id is None:
        raise StarterTemplateMissing(
            f"User '{settings.STARTER_TEMPLATE_USERNAME}' missing. "
            "Run `python -m app.fill_database` to seed it."
        )
    return user_id


async def copy_starter_pack(db: AsyncSession, new_user_id: int) -> None:
    """Copy muscles/exercises/exercise_muscle du starter_template vers le
    new_user_id. Idempotent : skip si le user a deja >=1 muscle. Leve
    StarterTemplateMissing si le fixture est absent (le router mappe en 503).

    Ne commit PAS : le caller decide (atomicite avec la creation du User
    parent dans /signup).
    """
    existing = await db.execute(
        select(Muscle.id).where(Muscle.user_id == new_user_id).limit(1)
    )
    if existing.scalar_one_or_none() is not None:
        return

    template_id = await _get_starter_template_user_id(db)

    # 1) Muscles : mapping {old_uuid: new_uuid}.
    template_muscles = (await db.execute(
        select(Muscle).where(Muscle.user_id == template_id)
    )).scalars().all()
    muscle_uuid_map: dict[str, str] = {}
    for tm in template_muscles:
        new_uuid = str(uuid.uuid4())
        muscle_uuid_map[tm.uuid] = new_uuid
        db.add(Muscle(
            uuid=new_uuid, user_id=new_user_id, name=tm.name,
            muscle_group=tm.muscle_group, zone=tm.zone, is_favorite=False,
        ))

    # 2) Exercises : mapping {old_uuid: new_uuid}.
    template_exercises = (await db.execute(
        select(Exercise).where(Exercise.user_id == template_id)
    )).scalars().all()
    exercise_uuid_map: dict[str, str] = {}
    for te in template_exercises:
        new_uuid = str(uuid.uuid4())
        exercise_uuid_map[te.uuid] = new_uuid
        db.add(Exercise(
            uuid=new_uuid, user_id=new_user_id, name=te.name,
            description=te.description, instructions=te.instructions,
            recommended_sets=te.recommended_sets,
            recommended_reps=te.recommended_reps,
            duration_in_seconds=te.duration_in_seconds,
            rest_time_seconds=te.rest_time_seconds,
            gif_url=te.gif_url, is_favorite=False,
        ))

    # Flush pour rendre les nouveaux uuids visibles aux INSERT relations
    # (contrainte FK sur exercise_uuid + muscle_uuid verifiee a chaque
    # statement, mais en meme transaction => OK).
    await db.flush()

    # 3) Relations exercise_muscle.
    template_relations = (await db.execute(
        select(ExerciseMuscle).where(
            ExerciseMuscle.exercise_uuid.in_(exercise_uuid_map.keys())
        )
    )).scalars().all()
    for tr in template_relations:
        db.add(ExerciseMuscle(
            uuid=str(uuid.uuid4()),
            exercise_uuid=exercise_uuid_map[tr.exercise_uuid],
            muscle_uuid=muscle_uuid_map[tr.muscle_uuid],
            coefficient=tr.coefficient,
        ))

    # 4) Quotes (motivational pack). Nouveaux uuids, pas de FK croisee.
    template_quotes = (await db.execute(
        select(Quote).where(Quote.user_id == template_id)
    )).scalars().all()
    for tq in template_quotes:
        db.add(Quote(
            uuid=str(uuid.uuid4()), user_id=new_user_id,
            text=tq.text, author=tq.author,
        ))

    # 5-7) Catalogue nutrition (foods + portions + meal presets).
    await copy_nutrition_pack(db, new_user_id)


async def copy_nutrition_pack(db: AsyncSession, user_id: int) -> None:
    """Copie le catalogue nutrition du starter_template vers user_id :
    foods (+ food_portions, mapping uuid) + 4 meal_presets par defaut (D10).

    Gardes d'idempotence SEPAREES (foods vs presets) : un user signe up avant
    l'import CIQUAL a deja ses presets mais 0 food — un backfill ulterieur
    (scripts/backfill_nutrition.py) doit pouvoir lui copier les foods sans
    dupliquer ses presets. Ne commit PAS : le caller decide (atomicite au
    /signup, commit par user dans le script de backfill).
    """
    template_id = await _get_starter_template_user_id(db)

    has_food = (await db.execute(
        select(Food.id).where(Food.user_id == user_id).limit(1)
    )).scalar_one_or_none() is not None
    if not has_food:
        # Foods : seed CIQUAL du template copie au user, avec mapping
        # {old_uuid: new_uuid} pour les portions.
        template_foods = (await db.execute(
            select(Food).where(Food.user_id == template_id)
        )).scalars().all()
        food_uuid_map: dict[str, str] = {}
        for tf in template_foods:
            new_uuid = str(uuid.uuid4())
            food_uuid_map[tf.uuid] = new_uuid
            db.add(Food(
                uuid=new_uuid, user_id=user_id,
                name=tf.name, brand=tf.brand,
                source=tf.source, source_ref=tf.source_ref,
                food_group=tf.food_group,
                kcal_per_100g=tf.kcal_per_100g,
                protein_per_100g=tf.protein_per_100g,
                carbs_per_100g=tf.carbs_per_100g,
                fat_per_100g=tf.fat_per_100g,
                fiber_per_100g=tf.fiber_per_100g,
                sugar_per_100g=tf.sugar_per_100g,
                sat_fat_per_100g=tf.sat_fat_per_100g,
                salt_per_100g=tf.salt_per_100g,
                iron_per_100g=tf.iron_per_100g,
                calcium_per_100g=tf.calcium_per_100g,
                magnesium_per_100g=tf.magnesium_per_100g,
                zinc_per_100g=tf.zinc_per_100g,
                potassium_per_100g=tf.potassium_per_100g,
                sodium_per_100g=tf.sodium_per_100g,
                vitamin_c_per_100g=tf.vitamin_c_per_100g,
                vitamin_d_per_100g=tf.vitamin_d_per_100g,
                vitamin_b12_per_100g=tf.vitamin_b12_per_100g,
                vitamin_a_per_100g=tf.vitamin_a_per_100g,
                is_favorite=False, archived=False,
            ))

        # Food portions du template (FK foods.uuid -> flush prealable).
        if food_uuid_map:
            await db.flush()
            template_portions = (await db.execute(
                select(FoodPortion).where(
                    FoodPortion.food_uuid.in_(food_uuid_map.keys())
                )
            )).scalars().all()
            for tp in template_portions:
                db.add(FoodPortion(
                    uuid=str(uuid.uuid4()),
                    food_uuid=food_uuid_map[tp.food_uuid],
                    label=tp.label, grams=tp.grams,
                ))

    has_preset = (await db.execute(
        select(MealPreset.id).where(MealPreset.user_id == user_id).limit(1)
    )).scalar_one_or_none() is not None
    if not has_preset:
        # Meal presets par defaut (D10) : 4 periodes habituelles du journal.
        for name, order_index, default_time in _DEFAULT_MEAL_PRESETS:
            db.add(MealPreset(
                uuid=str(uuid.uuid4()), user_id=user_id,
                name=name, order_index=order_index, default_time=default_time,
            ))
