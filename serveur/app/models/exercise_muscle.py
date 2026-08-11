# Exemple : app/models/exercise_muscle.py

from sqlalchemy import Column, Integer, Float, ForeignKey, String, DateTime, func
from app.database import Base

import uuid

class ExerciseMuscle(Base):
    __tablename__ = "exercise_muscles"

    id = Column(Integer, primary_key=True, index=True)

    exercise_uuid = Column(String, ForeignKey("exercises.uuid", ondelete="CASCADE"), nullable=False, index=True)
    muscle_uuid = Column(String, ForeignKey("muscles.uuid", ondelete="CASCADE"), nullable=False, index=True)
    coefficient = Column(Float, nullable=False, default=1.0, server_default="1")

    updated_at = Column(DateTime(timezone=True), nullable=True)

    uuid = Column(String, nullable=False, default=lambda: str(uuid.uuid4()), unique=True)