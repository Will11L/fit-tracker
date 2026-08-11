# Exemple : app/models/planned_workout.py

from sqlalchemy import Column, Integer, String, ForeignKey, DateTime, func
from sqlalchemy.orm import relationship
from app.database import Base

import uuid

class PlannedWorkout(Base):
    __tablename__ = "planned_workouts"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    name = Column(String, nullable=False)
    day_of_week = Column(String, nullable=False)

    uuid = Column(String, default=lambda: str(uuid.uuid4()), nullable=False, unique=True)

    updated_at = Column(DateTime(timezone=True), nullable=True)