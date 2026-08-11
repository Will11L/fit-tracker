# app/seed_database.py
"""
Seed de la base de dev/test, all in English (refactor 2026-05-07).

Strategie :
- starter_template (id=99999) : catalogue de reference 12 muscles EN + 20
  exercises EN + 43 relations EN. 6 zones granulaires (Chest / Back /
  Shoulders / Arms / Legs / Core) — alignees avec le mapping Android
  `MuscleGroups.kt` du chart Stats overview.
- Users `will` (id=1) et `bob` (id=2) recoivent leur copie du catalogue via
  `copy_starter_pack` (= simulation du /signup).
- `will` recoit en plus ~20 actual_workouts (Push/Pull/Legs templates) generes
  dynamiquement sur 6 semaines centrees today, avec progression de poids
  semaine par semaine. Les seances passees sont DONE, celle d'aujourd'hui
  est IN_PROGRESS, futures NOT_STARTED. Donne du grain pour tester le chart
  Stats / Muscle Stats / Exercise Stats.
- Tous les libelles UI (RoutinePeriod, RoutineTask) en anglais.
"""
import os
from __future__ import annotations

from datetime import date, datetime, timezone, timedelta
import secrets
import uuid

from sqlalchemy import select, text
from sqlalchemy.ext.asyncio import AsyncSession
from passlib.context import CryptContext

from app.settings import settings
from app.starter_pack import copy_starter_pack

from app.models.actual_workout_set import ActualWorkoutSet
from app.models.actual_workout_exercise import ActualWorkoutExercise
from app.models.actual_workout import ActualWorkout
from app.models.available_equipment import AvailableEquipment
from app.models.cycle_workout import CycleWorkout
from app.models.equipment import Equipment
from app.models.exercise_equipment import ExerciseEquipment
from app.models.exercise_muscle import ExerciseMuscle
from app.models.exercise import Exercise
from app.models.muscle_goal import MuscleGoal
from app.models.muscle import Muscle
from app.models.notification import Notification
from app.models.planned_workout_exercise import PlannedWorkoutExercise
from app.models.planned_workout import PlannedWorkout
from app.models.quote import Quote
from app.models.routine_period import RoutinePeriod
from app.models.task import Task
from app.models.task_check import TaskCheck
from app.models.superset_exercise import SupersetExercise
from app.models.superset_group import SupersetGroup
from app.models.training_cycle import TrainingCycle
from app.models.user import User

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


def to_iso_day(value=None) -> str:
    if value is None:
        return datetime.now(timezone.utc).date().isoformat()
    if isinstance(value, datetime):
        return value.astimezone(timezone.utc).date().isoformat()
    if isinstance(value, date):
        return value.isoformat()
    if isinstance(value, str):
        if len(value) == 10:
            return value
        raise ValueError("Invalid ISO date string")
    raise TypeError("Unsupported type for to_iso_day")


def hash_password(password: str) -> str:
    return pwd_context.hash(password)


async def safe_add_user(db: AsyncSession, user_id: int, username: str, password: str) -> None:
    by_id = await db.execute(select(User).where(User.id == user_id))
    if by_id.scalar_one_or_none():
        return
    by_username = await db.execute(select(User).where(User.username == username))
    if by_username.scalar_one_or_none():
        return
    db.add(User(id=user_id, username=username, hashed_password=hash_password(password)))


# ============================================================================
# Starter template catalog (canonical EN, 35 muscles / 20 exercises / 17 groups)
# ============================================================================
#
# Hierarchie 3 niveaux (refactor 2026-05-08, cf. CLAUDE.md historique) :
#   zone (6)         > muscle_group (17)  > muscle name (35)
#   ex: "Arms"       > "Triceps"          > "Triceps Long head"
#   ex: "Chest"      > "Pecs"             > "Mid Chest"
#
# Permet le drill-down stats (vue Zone large -> Group intermediaire -> Muscle
# precis) et le tracking serieux niveau coach/bodybuilder (delt posterieur
# neglige, long head triceps sous-travaille, etc).
#
# Format _STARTER_QUOTE_SPECS : (text, author | None). Pack de citations
# motivantes EN copie a chaque user au /signup (cf. copy_starter_pack).
_STARTER_QUOTE_SPECS: list[tuple[str, str | None]] = [
    ("The body achieves what the mind believes.", None),
    ("Success isn't always about greatness. It's about consistency.", "Dwayne Johnson"),
    ("The only bad workout is the one that didn't happen.", None),
    ("Strength does not come from the physical capacity. It comes from an indomitable will.", "Mahatma Gandhi"),
    ("Take care of your body. It's the only place you have to live.", "Jim Rohn"),
    ("Don't limit your challenges. Challenge your limits.", None),
    ("The pain you feel today will be the strength you feel tomorrow.", None),
    ("Discipline is choosing between what you want now and what you want most.", None),
]


# Format _STARTER_MUSCLE_SPECS : (name, muscle_group, zone)
_STARTER_MUSCLE_SPECS: list[tuple[str, str, str]] = [
    # === Chest (4) — group: Pecs ===
    ("Upper Chest",            "Pecs",            "Chest"),
    ("Mid Chest",              "Pecs",            "Chest"),
    ("Lower Chest",            "Pecs",            "Chest"),
    ("Pec Minor",              "Pecs",            "Chest"),
    # === Back (5) — groups: Lats, Rhomboids, Erector Spinae, Traps ===
    ("Lats",                   "Lats",            "Back"),
    ("Rhomboids",              "Rhomboids",       "Back"),
    ("Erector Spinae",         "Erector Spinae",  "Back"),
    ("Mid Traps",              "Traps",           "Back"),
    ("Upper Traps",            "Traps",           "Back"),
    # === Shoulders (3) — group: Delts ===
    ("Front Delt",             "Delts",           "Shoulders"),
    ("Side Delt",              "Delts",           "Shoulders"),
    ("Rear Delt",              "Delts",           "Shoulders"),
    # === Arms (7) — groups: Biceps, Triceps, Brachialis, Forearms ===
    ("Biceps Long head",       "Biceps",          "Arms"),
    ("Biceps Short head",      "Biceps",          "Arms"),
    ("Triceps Long head",      "Triceps",         "Arms"),
    ("Triceps Lateral head",   "Triceps",         "Arms"),
    ("Triceps Medial head",    "Triceps",         "Arms"),
    ("Brachialis",             "Brachialis",      "Arms"),
    ("Forearms",               "Forearms",        "Arms"),
    # === Legs (13) — groups: Quads, Hamstrings, Glutes, Calves, Adductors ===
    ("Vastus Lateralis",       "Quads",           "Legs"),
    ("Vastus Medialis",        "Quads",           "Legs"),
    ("Vastus Intermedius",     "Quads",           "Legs"),
    ("Rectus Femoris",         "Quads",           "Legs"),
    ("Biceps Femoris",         "Hamstrings",      "Legs"),
    ("Semitendinosus",         "Hamstrings",      "Legs"),
    ("Semimembranosus",        "Hamstrings",      "Legs"),
    ("Glute Max",              "Glutes",          "Legs"),
    ("Glute Med",              "Glutes",          "Legs"),
    ("Glute Min",              "Glutes",          "Legs"),
    ("Gastrocnemius",          "Calves",          "Legs"),
    ("Soleus",                 "Calves",          "Legs"),
    ("Adductors",              "Adductors",       "Legs"),
    # === Core (3) — groups: Abs, Obliques ===
    ("Rectus Abs",             "Abs",             "Core"),
    ("Obliques",               "Obliques",        "Core"),
    ("Transverse Abs",         "Abs",             "Core"),
]

# Coefficients exercise -> muscles : refactor 2026-05-08 sur les 35 muscles precis.
# Les coefs reflechissent le recrutement EMG anatomique (litterature + bon sens
# biomecanique). Un coef de 1.0 = muscle cible primaire, < 1.0 = muscle synergiste
# ou stabilisateur. Un set sur Bench Press contribue donc au volume Mid Chest a
# 100%, Lower Chest 60%, Upper Chest 30%, Triceps Long 50%, Triceps Lateral 50%,
# Front Delt 40%. Au niveau Group (Pecs = 1.0+0.6+0.3 = 1.9), ce qui reflete
# l'effort cumule sur toutes les fibres du group. Cf. Phase 1 analyse.
#
# Format : (name, description, instructions[3], sets, reps, rest, duration, [(muscle_name, coef)])
_STARTER_EXERCISE_SPECS: list[tuple[str, str, list[str], int, str, int, int | None, list[tuple[str, float]]]] = [
    ("Bench Press", "Compound chest press", [
        "Lie flat on the bench with feet planted on the floor",
        "Lower the bar to your mid-chest with control",
        "Press the bar up until your arms are fully extended",
    ], 4, "8-10", 90, None, [
        ("Mid Chest", 1.0), ("Lower Chest", 0.6), ("Upper Chest", 0.3),
        ("Triceps Long head", 0.5), ("Triceps Lateral head", 0.5),
        ("Front Delt", 0.4),
    ]),
    ("Incline Bench Press", "Upper chest press", [
        "Set the bench at a 30-45 degree incline",
        "Lower the bar to your upper chest",
        "Press the bar up while keeping your shoulders pinned back",
    ], 4, "8-10", 90, None, [
        ("Upper Chest", 1.0), ("Mid Chest", 0.5),
        ("Front Delt", 0.6),
        ("Triceps Long head", 0.5), ("Triceps Lateral head", 0.4),
    ]),
    ("Push-Up", "Bodyweight chest press", [
        "Place your hands shoulder-width apart on the floor",
        "Lower your chest until it nearly touches the ground",
        "Push back up while keeping your body in a straight line",
    ], 3, "10-15", 60, None, [
        ("Mid Chest", 1.0), ("Upper Chest", 0.4), ("Lower Chest", 0.4),
        ("Front Delt", 0.4),
        ("Triceps Long head", 0.4), ("Triceps Lateral head", 0.4),
        ("Rectus Abs", 0.3),
    ]),
    ("Dips", "Triceps and lower chest", [
        "Grip parallel bars with arms fully extended",
        "Lower your body by bending your elbows to about 90 degrees",
        "Press back up to the starting position",
    ], 3, "8-12", 60, None, [
        ("Lower Chest", 1.0), ("Mid Chest", 0.5),
        ("Triceps Long head", 0.9), ("Triceps Lateral head", 0.9), ("Triceps Medial head", 0.7),
        ("Front Delt", 0.4),
    ]),
    ("Pull-Up", "Bodyweight back and biceps", [
        "Grip the bar with palms facing away, hands wider than shoulders",
        "Pull yourself up until your chin clears the bar",
        "Lower yourself with control to a full hang",
    ], 3, "6-10", 90, None, [
        ("Lats", 1.0), ("Rhomboids", 0.5), ("Mid Traps", 0.4),
        ("Rear Delt", 0.3),
        ("Biceps Long head", 0.5), ("Biceps Short head", 0.6),
        ("Brachialis", 0.5), ("Forearms", 0.4),
    ]),
    ("Lat Pulldown", "Vertical back pull", [
        "Sit at the machine with thighs secured under the pads",
        "Pull the bar down to your upper chest while squeezing your back",
        "Slowly return the bar to the starting position",
    ], 3, "10-12", 60, None, [
        ("Lats", 1.0), ("Rhomboids", 0.4), ("Mid Traps", 0.3),
        ("Biceps Long head", 0.3), ("Biceps Short head", 0.4),
        ("Brachialis", 0.3),
    ]),
    ("Barbell Row", "Horizontal back pull", [
        "Hinge at the hips and grip the bar with hands shoulder-width apart",
        "Pull the bar to your lower chest while keeping your back flat",
        "Lower the bar with control without rounding your back",
    ], 4, "8-10", 90, None, [
        ("Rhomboids", 1.0), ("Mid Traps", 0.7), ("Lats", 0.8),
        ("Rear Delt", 0.5),
        ("Erector Spinae", 0.4),
        ("Biceps Long head", 0.4), ("Biceps Short head", 0.4), ("Forearms", 0.3),
    ]),
    ("Deadlift", "Full body posterior chain", [
        "Stand with feet hip-width apart and the bar over mid-foot",
        "Grip the bar and lift by extending hips and knees together",
        "Lower the bar with control by hinging at the hips first",
    ], 4, "5-8", 120, None, [
        ("Erector Spinae", 1.0),
        ("Biceps Femoris", 0.8), ("Semitendinosus", 0.7), ("Semimembranosus", 0.7),
        ("Glute Max", 0.9),
        ("Lats", 0.5), ("Upper Traps", 0.6), ("Mid Traps", 0.5),
        ("Forearms", 0.6),
    ]),
    ("Romanian Deadlift", "Hip hinge for hamstrings", [
        "Stand with feet hip-width apart, holding a barbell at hip height",
        "Hinge at the hips while keeping a slight bend in your knees",
        "Drive your hips forward to return to the standing position",
    ], 3, "8-10", 90, None, [
        ("Biceps Femoris", 1.0), ("Semitendinosus", 0.9), ("Semimembranosus", 0.9),
        ("Glute Max", 0.7),
        ("Erector Spinae", 0.5),
        ("Adductors", 0.3),
    ]),
    ("Squat", "Compound leg press", [
        "Place the bar across your upper back with feet shoulder-width apart",
        "Lower your hips down and back until thighs are at least parallel to the floor",
        "Drive through your heels to return to the standing position",
    ], 4, "6-10", 120, None, [
        ("Vastus Lateralis", 1.0), ("Vastus Medialis", 1.0),
        ("Vastus Intermedius", 0.9), ("Rectus Femoris", 0.7),
        ("Glute Max", 0.7), ("Glute Med", 0.4),
        ("Adductors", 0.5),
        ("Erector Spinae", 0.4),
        ("Biceps Femoris", 0.3),
    ]),
    ("Leg Press", "Machine leg drive", [
        "Sit on the machine with feet shoulder-width apart on the platform",
        "Lower the platform by bending your knees toward your chest",
        "Press the platform back up without locking your knees",
    ], 3, "10-12", 90, None, [
        ("Vastus Lateralis", 1.0), ("Vastus Medialis", 1.0),
        ("Vastus Intermedius", 0.9), ("Rectus Femoris", 0.5),
        ("Glute Max", 0.6),
        ("Adductors", 0.3),
    ]),
    ("Lunges", "Unilateral leg work", [
        "Stand tall with hands on hips or holding dumbbells",
        "Step forward and lower your hips until both knees are bent at 90 degrees",
        "Push through your front heel to return to the starting position",
    ], 3, "10-12", 60, None, [
        ("Vastus Lateralis", 0.8), ("Vastus Medialis", 0.8),
        ("Vastus Intermedius", 0.7), ("Rectus Femoris", 0.5),
        ("Glute Max", 0.8), ("Glute Med", 0.5),
        ("Biceps Femoris", 0.4),
        ("Adductors", 0.4),
    ]),
    ("Leg Extension", "Quadriceps isolation", [
        "Sit on the machine and place your shins behind the padded lever",
        "Extend your legs until straight, squeezing your quads at the top",
        "Lower the weight slowly back to the starting position",
    ], 3, "12-15", 60, None, [
        ("Vastus Lateralis", 1.0), ("Vastus Medialis", 1.0),
        ("Vastus Intermedius", 1.0), ("Rectus Femoris", 0.9),
    ]),
    ("Leg Curl", "Hamstring isolation", [
        "Lie face down on the machine with the pad behind your ankles",
        "Curl your heels toward your glutes by flexing your hamstrings",
        "Lower the weight under control to the starting position",
    ], 3, "10-15", 60, None, [
        ("Biceps Femoris", 1.0), ("Semitendinosus", 1.0), ("Semimembranosus", 0.9),
    ]),
    ("Calf Raises", "Calf isolation", [
        "Stand on a raised surface with the balls of your feet on the edge",
        "Push up onto your toes as high as possible",
        "Lower your heels below the platform for a full stretch",
    ], 3, "12-15", 60, None, [
        ("Gastrocnemius", 1.0), ("Soleus", 0.5),
    ]),
    ("Overhead Press", "Vertical shoulder push", [
        "Stand with the bar racked at shoulder height, feet shoulder-width apart",
        "Press the bar straight overhead while bracing your core",
        "Lower the bar back to your shoulders with control",
    ], 3, "6-10", 90, None, [
        ("Front Delt", 1.0), ("Side Delt", 0.5),
        ("Triceps Long head", 0.5), ("Triceps Lateral head", 0.5), ("Triceps Medial head", 0.6),
        ("Upper Traps", 0.4),
        ("Rectus Abs", 0.3),
    ]),
    ("Lateral Raise", "Lateral deltoid isolation", [
        "Stand tall with a dumbbell in each hand at your sides",
        "Raise the dumbbells out to the sides until they reach shoulder height",
        "Lower the weights slowly without swinging",
    ], 3, "12-15", 60, None, [
        ("Side Delt", 1.0), ("Front Delt", 0.2), ("Rear Delt", 0.1),
        ("Upper Traps", 0.2),
    ]),
    ("Bicep Curl", "Biceps isolation", [
        "Stand with a dumbbell in each hand, arms fully extended",
        "Curl the weights toward your shoulders while keeping your elbows pinned",
        "Lower the dumbbells slowly to the starting position",
    ], 3, "10-12", 60, None, [
        ("Biceps Long head", 0.7), ("Biceps Short head", 1.0),
        ("Brachialis", 0.5), ("Forearms", 0.4),
    ]),
    ("Tricep Extension", "Triceps isolation overhead", [
        "Hold a dumbbell with both hands and lift it overhead",
        "Lower the weight behind your head by bending your elbows",
        "Press the weight back up by extending your arms",
    ], 3, "10-12", 60, None, [
        ("Triceps Long head", 1.0), ("Triceps Lateral head", 0.4), ("Triceps Medial head", 0.5),
    ]),
    ("Plank", "Core stability hold", [
        "Place your forearms on the floor, elbows under shoulders",
        "Keep your body in a straight line from head to heels",
        "Hold the position while breathing steadily, without sagging",
    ], 3, "1", 45, 45, [
        ("Rectus Abs", 1.0), ("Transverse Abs", 0.9), ("Obliques", 0.6),
        ("Erector Spinae", 0.3),
    ]),
]


async def _seed_starter_template(db: AsyncSession, starter_id: int) -> None:
    has_catalog = (await db.execute(
        select(Muscle).where(Muscle.user_id == starter_id).limit(1)
    )).scalar_one_or_none()
    if has_catalog is not None:
        print("ℹ️  Starter template catalog already present, skipping.")
        return

    starter_muscle_uuids = {n: str(uuid.uuid4()) for n, _, _ in _STARTER_MUSCLE_SPECS}
    db.add_all([
        Muscle(uuid=starter_muscle_uuids[n], user_id=starter_id, name=n,
               muscle_group=g, zone=z, is_favorite=False)
        for n, g, z in _STARTER_MUSCLE_SPECS
    ])

    starter_exercise_uuids = {n: str(uuid.uuid4()) for n, *_ in _STARTER_EXERCISE_SPECS}
    db.add_all([
        Exercise(
            uuid=starter_exercise_uuids[n],
            user_id=starter_id,
            name=n,
            description=desc,
            instructions=instr,
            recommended_sets=sets,
            recommended_reps=reps,
            rest_time_seconds=rest,
            duration_in_seconds=duration,
            is_favorite=False,
        )
        for n, desc, instr, sets, reps, rest, duration, _ in _STARTER_EXERCISE_SPECS
    ])
    await db.commit()

    db.add_all([
        ExerciseMuscle(
            uuid=str(uuid.uuid4()),
            exercise_uuid=starter_exercise_uuids[ex_name],
            muscle_uuid=starter_muscle_uuids[m_name],
            coefficient=coef,
        )
        for ex_name, _, _, _, _, _, _, muscles in _STARTER_EXERCISE_SPECS
        for m_name, coef in muscles
    ])
    await db.commit()
    print("✅ Starter template catalog seeded (12 muscles + 20 exercises + 43 relations).")

    db.add_all([
        Quote(uuid=str(uuid.uuid4()), user_id=starter_id, text=text, author=author)
        for text, author in _STARTER_QUOTE_SPECS
    ])
    await db.commit()
    print(f"✅ Starter template quotes seeded ({len(_STARTER_QUOTE_SPECS)} quotes).")


async def _user_exercises_by_name(db: AsyncSession, user_id: int) -> dict[str, str]:
    rows = (await db.execute(
        select(Exercise.name, Exercise.uuid).where(Exercise.user_id == user_id)
    )).all()
    return {name: uuid_ for name, uuid_ in rows}


async def _user_muscles_by_name(db: AsyncSession, user_id: int) -> dict[str, str]:
    rows = (await db.execute(
        select(Muscle.name, Muscle.uuid).where(Muscle.user_id == user_id)
    )).all()
    return {name: uuid_ for name, uuid_ in rows}


# ============================================================================
# Actual workouts dynamic generator (6 weeks centered on today)
# ============================================================================

# Templates : (workout_name, [(exercise_name, [(reps, weight_base)])]).
# weight_base = poids cette semaine (semaine la plus recente). Chaque semaine
# precedente recule la progression linearement.

_PUSH_TEMPLATE = ("Push Day", [
    ("Bench Press",         [(8, 70.0), (8, 75.0), (6, 80.0), (5, 82.5)]),
    ("Incline Bench Press", [(10, 50.0), (10, 52.5), (8, 55.0)]),
    ("Overhead Press",      [(10, 35.0), (10, 37.5), (8, 40.0)]),
    ("Lateral Raise",       [(12, 8.0), (12, 9.0), (12, 10.0)]),
    ("Tricep Extension",    [(12, 15.0), (12, 17.5), (12, 17.5)]),
])

_PULL_TEMPLATE = ("Pull Day", [
    ("Pull-Up",     [(8, 0.0), (8, 0.0), (6, 5.0)]),
    ("Lat Pulldown", [(10, 50.0), (10, 55.0), (8, 60.0)]),
    ("Barbell Row", [(10, 50.0), (8, 55.0), (8, 60.0), (6, 65.0)]),
    ("Bicep Curl",  [(12, 12.0), (10, 14.0), (10, 14.0)]),
])

_LEG_TEMPLATE = ("Leg Day", [
    ("Squat",         [(8, 80.0), (8, 85.0), (6, 90.0), (5, 95.0)]),
    ("Leg Press",     [(12, 120.0), (10, 140.0), (8, 160.0)]),
    ("Romanian Deadlift", [(10, 70.0), (10, 75.0), (8, 80.0)]),
    ("Leg Extension", [(15, 40.0), (12, 45.0), (12, 50.0)]),
    ("Calf Raises",   [(15, 60.0), (15, 65.0), (12, 70.0)]),
])

_CORE_TEMPLATE = ("Core & Mobility", [
    ("Plank",         [(1, 0.0), (1, 0.0), (1, 0.0)]),
    ("Push-Up",       [(15, 0.0), (15, 0.0), (12, 0.0)]),
    ("Lunges",        [(10, 12.0), (10, 12.0), (10, 14.0)]),
])


async def _make_session(
    db: AsyncSession,
    user_id: int,
    name: str,
    workout_date: date,
    status: str,                 # "DONE" | "IN_PROGRESS" | "NOT_STARTED"
    exercises_specs: list[tuple[str, list[tuple[int, float]]]],
    exercise_uuids_by_name: dict[str, str],
    week_offset: int,            # negatif = passe ; 0 = cette semaine
) -> None:
    workout_uuid = str(uuid.uuid4())
    is_done = status == "DONE"
    db.add(ActualWorkout(
        uuid=workout_uuid,
        user_id=user_id,
        name=name,
        date=workout_date.isoformat(),
        is_done=is_done,
    ))

    # Progression realiste : ~0.4kg gagne par semaine (~20kg sur 1 an).
    # week_offset=-52 -> -20.8kg vs cette semaine ; week_offset=-5 -> -2kg.
    weight_delta = float(week_offset) * 0.4

    for order, (ex_name, sets_data) in enumerate(exercises_specs, start=1):
        ex_uuid = exercise_uuids_by_name.get(ex_name)
        if ex_uuid is None:
            continue  # exercise pas dans le catalogue — silencieux

        awe_uuid = str(uuid.uuid4())
        ex_status = (
            "DONE" if is_done
            else "IN_PROGRESS" if status == "IN_PROGRESS" and order <= len(exercises_specs) // 2
            else "NOT_STARTED"
        )
        first_reps = sets_data[0][0] if sets_data else 8
        db.add(ActualWorkoutExercise(
            uuid=awe_uuid,
            actual_workout_uuid=workout_uuid,
            exercise_uuid=ex_uuid,
            sets=len(sets_data),
            reps=str(first_reps),
            phase="TRAINING",
            status=ex_status,
            order=order,
            added_manually=False,
        ))

        for set_idx, (reps, weight_base) in enumerate(sets_data, start=1):
            # Set en cours = quelques DONE + restants NOT_STARTED si IN_PROGRESS
            if is_done:
                set_status = "DONE"
            elif status == "IN_PROGRESS" and order <= len(exercises_specs) // 2 and set_idx <= 2:
                set_status = "DONE"
            else:
                set_status = "NOT_STARTED"

            if weight_base > 0:
                actual_weight = max(0.0, weight_base + weight_delta)
            else:
                actual_weight = 0.0
            actual_reps = reps if set_status == "DONE" else 0

            db.add(ActualWorkoutSet(
                uuid=str(uuid.uuid4()),
                actual_workout_exercise_uuid=awe_uuid,
                set_order=set_idx,
                reps=actual_reps,
                weight=actual_weight,
                is_dropset=False,
                status=set_status,
            ))


# Nombre de semaines de seances backfilled pour will. ~1 an = 52 semaines.
# Genere ~150 sessions Push/Pull/Legs + ~26 Core (1/2 semaines). Avec 4-5
# exercises par session = ~700 awe et ~2300 sets. Volume riche pour tester
# les ranges 30 days / 3 months / 6 months / 1 year sans ecran vide.
_WILL_WEEKS_BACK = 52


async def _seed_will_workouts(db: AsyncSession, will_id: int, today: date) -> None:
    """~1 an de seances pour will (52 semaines). Lundi=Push, Mardi=Pull,
    Mercredi=Legs, Vendredi=Core (1 sem sur 2). Today's session = IN_PROGRESS
    (partial DONE) ; passe = DONE ; futur = NOT_STARTED."""
    exercise_uuids_by_name = await _user_exercises_by_name(db, will_id)
    if not exercise_uuids_by_name:
        print("⚠️  No exercises for will, skipping actual_workouts seed.")
        return

    has_workouts = (await db.execute(
        select(ActualWorkout).where(ActualWorkout.user_id == will_id).limit(1)
    )).scalar_one_or_none()
    if has_workouts is not None:
        print("ℹ️  will already has actual_workouts, skipping.")
        return

    today_monday = today - timedelta(days=today.weekday())
    sessions_count = 0

    for week_offset in range(-_WILL_WEEKS_BACK, 1):
        monday = today_monday + timedelta(weeks=week_offset)
        push_date = monday
        pull_date = monday + timedelta(days=1)
        leg_date = monday + timedelta(days=2)
        core_date = monday + timedelta(days=4)  # Friday

        for session_date, template in [
            (push_date, _PUSH_TEMPLATE),
            (pull_date, _PULL_TEMPLATE),
            (leg_date, _LEG_TEMPLATE),
        ]:
            if session_date < today:
                status = "DONE"
            elif session_date == today:
                status = "IN_PROGRESS"
            else:
                status = "NOT_STARTED"

            await _make_session(
                db, will_id, template[0], session_date, status,
                template[1], exercise_uuids_by_name, week_offset,
            )
            sessions_count += 1

        if week_offset % 2 == 0:
            status = "DONE" if core_date < today else "NOT_STARTED"
            await _make_session(
                db, will_id, _CORE_TEMPLATE[0], core_date, status,
                _CORE_TEMPLATE[1], exercise_uuids_by_name, week_offset,
            )
            sessions_count += 1

        # Commit periodique pour ne pas avoir un transaction enorme
        if week_offset % 8 == 0:
            await db.commit()

    await db.commit()
    print(f"✅ will : {_WILL_WEEKS_BACK + 1} weeks of actual_workouts seeded ({sessions_count} sessions).")


# ============================================================================
# Main seed entry
# ============================================================================

async def seed_all(db: AsyncSession) -> None:
    # -------------------- Users --------------------
    # Mot de passe des comptes de seed : jamais en dur dans le depot.
    # Defini par SEED_USER_PASSWORD (.env, non versionne) ; defaut inoffensif.
    seed_pwd = settings.SEED_USER_PASSWORD
    await safe_add_user(db, 1, "will", seed_pwd)
    await safe_add_user(db, 2, "bob", seed_pwd)
    await safe_add_user(db, 3, "charlie", seed_pwd)
    await safe_add_user(db, 4, "diana", seed_pwd)
    await safe_add_user(db, 5, "eve", seed_pwd)

    template_user = (await db.execute(
        select(User).where(User.username == settings.STARTER_TEMPLATE_USERNAME)
    )).scalar_one_or_none()
    if template_user is None:
        db.add(User(
            id=99999,
            username=settings.STARTER_TEMPLATE_USERNAME,
            hashed_password=hash_password(secrets.token_urlsafe(32)),
            is_admin=False,
        ))

    await db.commit()
    await db.execute(
        text("SELECT setval('users_id_seq', (SELECT COALESCE(MAX(id), 0) FROM users WHERE username != :u))"),
        {"u": settings.STARTER_TEMPLATE_USERNAME},
    )
    await db.commit()
    print("✅ Users seeded successfully (incl. starter_template).")

    # -------------------- Starter template catalog (EN) --------------------
    await _seed_starter_template(db, starter_id=99999)

    # -------------------- Copy starter to will + bob --------------------
    # Simulate /signup behaviour : duplicate the catalog into each test user.
    await copy_starter_pack(db, new_user_id=1)
    await copy_starter_pack(db, new_user_id=2)
    await db.commit()
    print("✅ Catalog copied to will (1) + bob (2) via copy_starter_pack.")

    # -------------------- Will : actual workouts (dynamic, ~6 weeks) --------------------
    today = datetime.now(timezone.utc).date()
    await _seed_will_workouts(db, will_id=1, today=today)

    # -------------------- Available Equipment (will) --------------------
    has_avail = (await db.execute(
        select(AvailableEquipment).where(AvailableEquipment.user_id == 1).limit(1)
    )).scalar_one_or_none()
    if has_avail is None:
        db.add_all([
            AvailableEquipment(uuid=str(uuid.uuid4()), name="Dumbbells", user_id=1),
            AvailableEquipment(uuid=str(uuid.uuid4()), name="Barbell", user_id=1),
            AvailableEquipment(uuid=str(uuid.uuid4()), name="Resistance Bands", user_id=1),
            AvailableEquipment(uuid=str(uuid.uuid4()), name="Kettlebell", user_id=1),
            AvailableEquipment(uuid=str(uuid.uuid4()), name="Pull-up Bar", user_id=1),
        ])
        await db.commit()
        print("✅ Available equipment seeded for will.")

    # -------------------- Equipment (global Type C) --------------------
    has_eq = (await db.execute(select(Equipment).limit(1))).scalar_one_or_none()
    if has_eq is None:
        db.add_all([
            Equipment(uuid=str(uuid.uuid4()), name="Barbell"),
            Equipment(uuid=str(uuid.uuid4()), name="Dumbbell"),
            Equipment(uuid=str(uuid.uuid4()), name="Machine"),
            Equipment(uuid=str(uuid.uuid4()), name="Kettlebell"),
            Equipment(uuid=str(uuid.uuid4()), name="Bodyweight"),
        ])
        await db.commit()
        print("✅ Equipment seeded (global).")

    # -------------------- MuscleGoals (will, current week) --------------------
    iso_week = today.strftime("%G-W%V")
    has_goals = (await db.execute(
        select(MuscleGoal).where(MuscleGoal.user_id == 1, MuscleGoal.week_iso == iso_week).limit(1)
    )).scalar_one_or_none()
    if has_goals is None:
        will_muscles = await _user_muscles_by_name(db, user_id=1)
        # 4 goals on iconic precise muscles (post-refactor 2026-05-08, anciens
        # noms genericos "Chest"/"Back"/"Quads"/"Abs" remplaces par les muscles
        # precis correspondants au plus saillant dans chaque zone).
        for muscle_name, target, priority in [
            ("Mid Chest",        "12", "HIGH"),
            ("Lats",             "12", "HIGH"),
            ("Vastus Lateralis", "10", "MEDIUM"),
            ("Rectus Abs",        "8", "LOW"),
        ]:
            muscle_uuid = will_muscles.get(muscle_name)
            if muscle_uuid is None:
                continue
            db.add(MuscleGoal(
                uuid=str(uuid.uuid4()),
                user_id=1,
                muscle_uuid=muscle_uuid,
                priority=priority,
                done=0,
                target=target,
                added_manually=True,
                status="IN_PROGRESS",
                week_iso=iso_week,
            ))
        await db.commit()
        print("✅ Muscle goals seeded for will (current week).")

    # -------------------- Planned workouts (will) --------------------
    has_planned = (await db.execute(
        select(PlannedWorkout).where(PlannedWorkout.user_id == 1).limit(1)
    )).scalar_one_or_none()
    if has_planned is None:
        will_exercises = await _user_exercises_by_name(db, user_id=1)
        pw_specs = [
            ("Push Day",        "Monday",    [("Bench Press", 4, "8-10", "WARMUP"),
                                              ("Overhead Press", 3, "8-10", "TRAINING"),
                                              ("Tricep Extension", 3, "10-12", "POST_TRAINING")]),
            ("Pull Day",        "Tuesday",   [("Pull-Up", 3, "6-10", "WARMUP"),
                                              ("Barbell Row", 4, "8-10", "TRAINING"),
                                              ("Bicep Curl", 3, "10-12", "POST_TRAINING")]),
            ("Leg Day",         "Wednesday", [("Squat", 4, "6-10", "WARMUP"),
                                              ("Leg Press", 3, "10-12", "TRAINING"),
                                              ("Calf Raises", 3, "12-15", "POST_TRAINING")]),
            ("Core & Mobility", "Friday",    [("Plank", 3, "1", "TRAINING"),
                                              ("Push-Up", 3, "12-15", "TRAINING")]),
            ("Full Body",       "Sunday",    [("Squat", 3, "8-10", "WARMUP"),
                                              ("Bench Press", 3, "8-10", "TRAINING"),
                                              ("Pull-Up", 3, "6-10", "TRAINING")]),
        ]
        for pw_name, day, exercises in pw_specs:
            pw_uuid = str(uuid.uuid4())
            db.add(PlannedWorkout(uuid=pw_uuid, user_id=1, name=pw_name, day_of_week=day))
            for order, (ex_name, sets, reps, phase) in enumerate(exercises, start=1):
                ex_uuid = will_exercises.get(ex_name)
                if ex_uuid is None:
                    continue
                db.add(PlannedWorkoutExercise(
                    uuid=str(uuid.uuid4()),
                    planned_workout_uuid=pw_uuid,
                    exercise_uuid=ex_uuid,
                    sets=sets,
                    reps=reps,
                    phase=phase,
                    status="PLANNED",
                    order=order,
                    ignored=False,
                ))
        await db.commit()
        print("✅ Planned workouts seeded for will (5 templates).")

    # -------------------- Routine Periods + Tasks (will) --------------------
    has_periods = (await db.execute(
        select(RoutinePeriod).where(RoutinePeriod.user_id == 1).limit(1)
    )).scalar_one_or_none()
    if has_periods is None:
        period_uuids = [str(uuid.uuid4()) for _ in range(3)]
        db.add_all([
            RoutinePeriod(uuid=period_uuids[0], user_id=1, name="Morning",
                          start_time="06:30", end_time="09:00", order=1,
                          reminder_before_start_minutes=0),
            RoutinePeriod(uuid=period_uuids[1], user_id=1, name="Midday",
                          start_time="12:00", end_time="14:00", order=2,
                          reminder_before_start_minutes=0),
            RoutinePeriod(uuid=period_uuids[2], user_id=1, name="Evening",
                          start_time="20:00", end_time="22:30", order=3,
                          reminder_before_start_minutes=0),
        ])
        task_uuids = [str(uuid.uuid4()) for _ in range(6)]
        today_iso = today.isoformat()
        db.add_all([
            Task(uuid=task_uuids[0], user_id=1, period_uuid=period_uuids[0],
                 title="Stretch routine", order=1,
                 recurrence_kind="DAILY", recurrence_start_date=today_iso),
            Task(uuid=task_uuids[1], user_id=1, period_uuid=period_uuids[0],
                 title="Weigh in (fasted)", order=2,
                 recurrence_kind="DAILY", recurrence_start_date=today_iso),
            Task(uuid=task_uuids[2], user_id=1, period_uuid=period_uuids[1],
                 title="Drink 1L of water", order=1,
                 recurrence_kind="DAILY", recurrence_start_date=today_iso),
            Task(uuid=task_uuids[3], user_id=1, period_uuid=period_uuids[2],
                 title="Spinal decompression hang", order=1,
                 recurrence_kind="DAILY", recurrence_start_date=today_iso),
            Task(uuid=task_uuids[4], user_id=1, period_uuid=period_uuids[2],
                 title="5 min breathing", order=2,
                 recurrence_kind="DAILY", recurrence_start_date=today_iso),
            Task(uuid=task_uuids[5], user_id=1, period_uuid=period_uuids[2],
                 title="Prepare gym outfit", order=3,
                 recurrence_kind="DAILY", recurrence_start_date=today_iso),
        ])
        # Today's checks
        db.add_all([
            TaskCheck(uuid=str(uuid.uuid4()), user_id=1, task_uuid=task_uuids[0],
                      occurrence_date=today_iso, is_checked=True, checked_at=datetime.utcnow()),
            TaskCheck(uuid=str(uuid.uuid4()), user_id=1, task_uuid=task_uuids[1],
                      occurrence_date=today_iso, is_checked=False),
            TaskCheck(uuid=str(uuid.uuid4()), user_id=1, task_uuid=task_uuids[3],
                      occurrence_date=today_iso, is_checked=True, checked_at=datetime.utcnow()),
        ])
        await db.commit()
        print("✅ Routine periods + tasks + checks seeded for will (EN).")

    # -------------------- Notifications (will) --------------------
    has_notif = (await db.execute(
        select(Notification).where(Notification.user_id == 1).limit(1)
    )).scalar_one_or_none()
    if has_notif is None:
        db.add_all([
            Notification(
                uuid=str(uuid.uuid4()), user_id=1,
                type="ROUTINE_PERIOD_START", level="info",
                title="3 tasks to complete",
                body="Within the time range 06:30 – 09:00",
                data={"period": "Morning", "startTime": "06:30",
                      "endTime": "09:00", "taskCount": 3, "screen": "routine"},
            ),
            Notification(
                uuid=str(uuid.uuid4()), user_id=1,
                type="TIMER_DONE", level="success",
                title="Timer finished ⏱️",
                body="Rest time is over, ready for the next set",
                data={"timerType": "rest", "durationSeconds": 90,
                      "exercise": "Bench Press", "screen": "workout"},
            ),
            Notification(
                uuid=str(uuid.uuid4()), user_id=1,
                type="WORKOUT_COMPLETED", level="success",
                title="Workout completed 💪",
                body="Great job! You have completed today's workout",
                data={"workoutName": "Push Day", "totalExercises": 5,
                      "screen": "workout_summary"},
            ),
        ])
        await db.commit()
        print("✅ Notifications seeded for will.")

    print("===== ✅ Seed complete (English, dataset will rich on dates around today). =====")
