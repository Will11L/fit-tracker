# Exemple : app/models/superset.py

from sqlalchemy import Column, Integer, ForeignKey, String, DateTime, func
from sqlalchemy.orm import relationship, declarative_base
from app.database import Base

import uuid

class SupersetExercise(Base):
    __tablename__ = "superset_exercises"

    id = Column(Integer, primary_key=True, index=True)  # ClÃ© technique
    superset_group_uuid = Column(String, ForeignKey("superset_groups.uuid", ondelete="CASCADE"), nullable=False, index=True)
    exercise_uuid = Column(String, ForeignKey("exercises.uuid", ondelete="CASCADE"), nullable=False, index=True)
    order_in_group = Column(Integer, nullable=True)

    uuid = Column(String, default=lambda: str(uuid.uuid4()), nullable=False, unique=True)

    updated_at = Column(DateTime(timezone=True), nullable=True)
