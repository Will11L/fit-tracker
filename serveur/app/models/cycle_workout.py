from sqlalchemy import Column, Integer, ForeignKey, String, DateTime, func
from app.database import Base

import uuid

class CycleWorkout(Base):
    __tablename__ = "cycle_workouts"

    id = Column(Integer, primary_key=True, index=True)
    uuid = Column(String, nullable=False, default=lambda: str(uuid.uuid4()), unique=True)

    training_cycle_uuid = Column(String, ForeignKey("training_cycles.uuid", ondelete="CASCADE"), nullable=False, index=True)
    planned_workout_uuid = Column(String, ForeignKey("planned_workouts.uuid", ondelete="CASCADE"), nullable=False, index=True)

    updated_at = Column(DateTime(timezone=True), nullable=True)