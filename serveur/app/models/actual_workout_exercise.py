from sqlalchemy import Column, Integer, ForeignKey, String, Boolean, DateTime, func
from app.database import Base

import uuid

class ActualWorkoutExercise(Base):
    __tablename__ = "actual_workout_exercises"

    id = Column(Integer, primary_key=True, index=True)

    actual_workout_uuid = Column(String, ForeignKey("actual_workouts.uuid", ondelete="CASCADE"), nullable=False, index=True)
    exercise_uuid = Column(String, ForeignKey("exercises.uuid", ondelete="CASCADE"), nullable=False, index=True)

    sets = Column(Integer, nullable=False, default=0)               # Number of sets for the exercise
    reps = Column(String, nullable=False, default="0-1")            # Number of repetitions for the exercise
    phase = Column(String, nullable=False, default="TRAINING", server_default="TRAINING")  # e.g., "WARMUP", "TRAINING", "POST_TRAINING"
    status = Column(String, nullable=False, default="NOT_STARTED")  # e.g., "NOT_STARTED", "IN_PROGRESS", "DONE", "SKIPPED"
    order = Column(Integer, nullable=False, default=0)              # Order of the exercise in the workout
    added_manually = Column(Boolean, default=False)                 # Indicates if the exercise was added manually by the user

    updated_at = Column(DateTime(timezone=True), nullable=True)

    uuid = Column(String, default=lambda: str(uuid.uuid4()), nullable=False, unique=True)