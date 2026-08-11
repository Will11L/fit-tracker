# app/clear_database.py
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import delete

from app.models import (
    actual_workout_set, actual_workout_exercise,
    actual_workout, available_equipment, cycle_workout,
    equipment, exercise_equipment, exercise_muscle, exercise,
    muscle_goal, muscle,
    planned_workout_exercise, planned_workout,
    routine_period, task, task_check,
    superset_exercise, superset_group, training_cycle, user
)

async def clear_all_tables_except_users(db: AsyncSession):
    # Pas d'emoji ici : crash sur Windows cp1252 (cf. seed_database F6-3 fix).
    print("[clear] Wiping table contents (preserving users)...")

    # Respect des FK : on supprime du plus dépendant au moins dépendant
    await db.execute(delete(actual_workout_set.ActualWorkoutSet))
    await db.execute(delete(actual_workout_exercise.ActualWorkoutExercise))
    await db.execute(delete(actual_workout.ActualWorkout))
    await db.execute(delete(available_equipment.AvailableEquipment))
    await db.execute(delete(cycle_workout.CycleWorkout))
    await db.execute(delete(equipment.Equipment))
    await db.execute(delete(exercise_equipment.ExerciseEquipment))
    await db.execute(delete(exercise_muscle.ExerciseMuscle))
    await db.execute(delete(exercise.Exercise))
    await db.execute(delete(muscle_goal.MuscleGoal))
    await db.execute(delete(muscle.Muscle))
    await db.execute(delete(planned_workout_exercise.PlannedWorkoutExercise))
    await db.execute(delete(planned_workout.PlannedWorkout))
    # Phase 0 (2026-05-12) : task_checks AVANT tasks (FK CASCADE), AVANT
    # routine_periods (FK SET NULL depuis tasks.period_uuid).
    await db.execute(delete(task_check.TaskCheck))
    await db.execute(delete(task.Task))
    await db.execute(delete(routine_period.RoutinePeriod))
    await db.execute(delete(superset_exercise.SupersetExercise))
    await db.execute(delete(superset_group.SupersetGroup))
    await db.execute(delete(training_cycle.TrainingCycle))

    # ⚠️ On NE PASSE pas la table users
    # await db.execute(delete(user.User))

    await db.commit()
    print("[clear] Done.")
