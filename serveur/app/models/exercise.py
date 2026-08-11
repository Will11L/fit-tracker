# Exemple : app/models/exercise.py

from sqlalchemy import Column, Integer, String, ForeignKey, Boolean, DateTime, UniqueConstraint, func
from sqlalchemy.dialects.postgresql import JSONB
from app.database import Base

import uuid

class Exercise(Base):
    __tablename__ = "exercises"
    __table_args__ = (
        UniqueConstraint("user_id", "name", name="uq_exercises_user_id_name"),
    )

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)

    uuid = Column(String, default=lambda: str(uuid.uuid4()), nullable=False, unique=True)

    name = Column(String, nullable=False)
    description = Column(String, nullable=True)
    instructions = Column(JSONB, nullable=True)
    recommended_sets = Column(Integer, nullable=True)
    recommended_reps = Column(String, nullable=True)
    duration_in_seconds = Column(Integer, nullable=True)
    rest_time_seconds = Column(Integer, nullable=True)
    gif_url = Column(String, nullable=True)
    is_favorite = Column(Boolean, default=False)
    last_done = Column(String, nullable=True)

    updated_at = Column(DateTime(timezone=True), nullable=True)
