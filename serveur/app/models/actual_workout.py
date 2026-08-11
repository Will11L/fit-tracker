# Exemple : app/models/actual_workout.py

from sqlalchemy import Column, Integer, String, DateTime, ForeignKey, Boolean, func
from app.database import Base

import uuid

class ActualWorkout(Base):
    __tablename__ = "actual_workouts"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    name = Column(String, nullable=False)
    date = Column(String, nullable=False)   # au format ISO : YYYY-MM-DD
    notes = Column(String, nullable=True)
    location = Column(String, nullable=True)
    is_done = Column(Boolean, default=False, nullable=False)

    updated_at = Column(DateTime(timezone=True), nullable=True)

    uuid = Column(String, default=lambda: str(uuid.uuid4()), nullable=False, unique=True)