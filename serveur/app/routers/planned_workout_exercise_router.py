from fastapi import APIRouter, Depends, HTTPException, Body
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession
from app import schemas, crud
from app.database import get_session
from app.dependencies import get_current_user_id

planned_workout_exercise_router = APIRouter(tags=["planned_workout_exercises"])

@planned_workout_exercise_router.get(
    "/planned-workout-exercises",
    response_model=list[schemas.PlannedWorkoutExerciseOut]
)
async def get_all_planned_exercises(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    results = await crud.get_all_planned_workout_exercises_for_user(db, user_id)
    return jsonable_encoder(results, by_alias=True)

@planned_workout_exercise_router.get(
    "/planned-workout-exercises/{uuid}",
    response_model=schemas.PlannedWorkoutExerciseOut
)
async def read_planned_workout_exercise(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    exercise = await crud.get_planned_workout_exercise_by_uuid(db, uuid)
    if not exercise:
        raise HTTPException(status_code=404, detail="Exercice non trouvé ou accès interdit")
    owned = await crud.is_planned_workout_owned_by_user_uuid(db, exercise.planned_workout_uuid, user_id)
    if not owned:
        raise HTTPException(status_code=404, detail="Exercice non trouvé ou accès interdit")
    return jsonable_encoder(exercise, by_alias=True)

@planned_workout_exercise_router.put(
    "/planned-workout-exercises/bulk",
    response_model=list[schemas.PlannedWorkoutExerciseOut]
)
async def upsert_bulk_exercises(
    exercises: list[schemas.PlannedWorkoutExerciseCreate] = Body(default=[]),
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    if not exercises:
        return []
    # Vérification d’appartenance pour chaque payload
    for ex in exercises:
        owned = await crud.is_planned_workout_owned_by_user_uuid(db, ex.planned_workout_uuid, user_id)
        if not owned:
            raise HTTPException(status_code=403, detail=f"Accès interdit à la séance {ex.planned_workout_uuid}")
    results = await crud.upsert_many_planned_workout_exercises(db, exercises, user_id)
    return jsonable_encoder(results, by_alias=True)

@planned_workout_exercise_router.put(
    "/planned-workout-exercises/{uuid}",
    response_model=schemas.PlannedWorkoutExerciseOut
)
async def upsert_planned_workout_exercise(
    uuid: str,
    planned_exercise: schemas.PlannedWorkoutExerciseCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id)
):
    result = await crud.upsert_planned_workout_exercise(db, uuid, planned_exercise, user_id)
    return jsonable_encoder(result, by_alias=True)

@planned_workout_exercise_router.delete(
    "/planned-workout-exercises/{exercise_uuid}",
    response_model=dict
)
async def delete_exercise_from_planned_workout(
    exercise_uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    success = await crud.delete_planned_workout_exercise_by_uuid(db, exercise_uuid, user_id)
    if not success:
        raise HTTPException(status_code=404, detail="Exercice non trouvé ou accès interdit")
    return {"detail": "Exercice supprimé avec succès"}