# Exemple : app/models/actual_workout_set.py

from sqlalchemy import Column, Integer, Float, Boolean, ForeignKey, String, DateTime, func
from app.database import Base

import uuid

class ActualWorkoutSet(Base):
    __tablename__ = "actual_workout_sets"

    id = Column(Integer, primary_key=True, index=True)
    actual_workout_exercise_uuid = Column(String, ForeignKey("actual_workout_exercises.uuid", ondelete="CASCADE"), nullable=False, index=True)
    set_order = Column(Integer, nullable=False)
    reps = Column(Integer, nullable=False)
    weight = Column(Float, nullable=False)
    is_dropset = Column(Boolean, nullable=False, default=False, server_default="false")
    notes = Column(String, nullable=True)
    recommendation = Column(String, nullable=True)
    status = Column(String, nullable=False, default="NOT_STARTED", server_default="NOT_STARTED")

    updated_at = Column(DateTime(timezone=True), nullable=True)

    uuid = Column(String, default=lambda: str(uuid.uuid4()), nullable=False, unique=True)
