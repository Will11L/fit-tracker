# app/view_database.py
import asyncio
from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import AsyncSessionLocal
from app.models import *

async def count_rows(session: AsyncSession, model) -> int:
    # SELECT COUNT(*) FROM model
    result = await session.execute(
        select(func.count()).select_from(model)
    )
    return result.scalar_one()

async def print_table(session: AsyncSession, model, limit: int = 10):
    total = await count_rows(session, model)
    print(f"{total} rows found in table: {model.__tablename__}")

    print(f"\n--- {model.__tablename__} (top {limit}) ---")
    # IMPORTANT: select(model) (et pas model.__table__) pour avoir des instances ORM
    result = await session.execute(select(model).limit(limit))
    rows = result.scalars().all()  # -> liste d’instances ORM

    for row in rows:
        clean_dict = {c.name: getattr(row, c.name) for c in row.__table__.columns}
        print(clean_dict)

    print("\n" + "-" * 40 + "\n")

async def main():
    async with AsyncSessionLocal() as session:
        models = [
            ActualWorkout,
            ActualWorkoutSet,
            AvailableEquipment,
            CycleWorkout,
            Equipment,
            ExerciseEquipment,
            ExerciseMuscle,
            Exercise,
            MuscleGoal,
            Muscle,
            Notification,
            PlannedWorkoutExercise,
            PlannedWorkout,
            RoutinePeriod,
            Task,
            TaskCheck,
            SupersetExercise,
            SupersetGroup,
            TrainingCycle,
            User,
            ActualWorkoutExercise,
        ]
        for model in models:
            await print_table(session, model)

if __name__ == "__main__":
    asyncio.run(main())
