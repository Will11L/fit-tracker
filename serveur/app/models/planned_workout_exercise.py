# Exemple : app/models/planned_workout_exercise.py

from sqlalchemy import Column, Integer, ForeignKey, String, Boolean, DateTime, func
from app.database import Base

import uuid

class PlannedWorkoutExercise(Base):
    __tablename__ = "planned_workout_exercises"

    id = Column(Integer, primary_key=True, index=True)
    planned_workout_uuid = Column(String, ForeignKey("planned_workouts.uuid", ondelete="CASCADE"), nullable=False, index=True)
    exercise_uuid = Column(String, ForeignKey("exercises.uuid", ondelete="CASCADE"), nullable=False, index=True)

    sets = Column(Integer, nullable=False)
    reps = Column(String, nullable=False)
    phase = Column(String, nullable=False, default="TRAINING")
    status = Column(String, nullable=False, default="PLANNED")
    order = Column(Integer, nullable=False, default=0)
    ignored = Column(Boolean, default=False, nullable=False)

    uuid = Column(String, default=lambda: str(uuid.uuid4()), nullable=False, unique=True)

    updated_at = Column(DateTime(timezone=True), nullable=True)