from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime
from app.utc_datetime import UTCDateTime

# -------------------- ActualWorkoutExercise --------------------

class ActualWorkoutExerciseBase(BaseModel):
    actual_workout_uuid: str = Field(..., alias="actualWorkoutUUID")  # UUID of the related ActualWorkout
    exercise_uuid: str = Field(..., alias="exerciseUUID")  # UUID of the Exercise
    sets: int = 0  # Number of sets for the exercise
    reps: str  # Number of repetitions for the exercise
    phase: str  # e.g., "WARMUP", "TRAINING", "POST_TRAINING"
    status: str  # e.g., "NOT_STARTED", "IN_PROGRESS", "DONE", "SKIPPED"
    order: int = 0  # Order of the exercise in the workout

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")  # Last update timestamp

    uuid: str  # Unique identifier (canonique : toujours fourni par le client)
    model_config = {"populate_by_name": True}

class ActualWorkoutExerciseCreate(ActualWorkoutExerciseBase):
    added_manually: bool = Field(False, alias="addedManually")  # Indicates if the exercise was added manually by the user

class ActualWorkoutExerciseOut(ActualWorkoutExerciseBase):
    added_manually: bool = Field(False, alias="addedManually")  # Indicates if the exercise was added manually by the user

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,  # allows accepting both snake_case and camelCase in input
    }
