from pydantic import BaseModel, Field
from typing import Optional, List
from datetime import datetime
from app.utc_datetime import UTCDateTime

# -------------------- Exercise --------------------

class ExerciseBase(BaseModel):
    name: str
    description: Optional[str] = None
    instructions: Optional[List[str]] = None
    recommended_sets: Optional[int] = Field(None, alias="recommendedSets")
    recommended_reps: Optional[str] = Field(None, alias="recommendedReps")
    rest_time_seconds: Optional[int] = Field(None, alias="restTimeSeconds")
    duration_in_seconds: Optional[int] = Field(None, alias="durationInSeconds")
    gif_url: Optional[str] = Field(None, alias="gifUrl")
    is_favorite: bool = Field(..., alias="isFavorite")
    last_done: Optional[str] = Field(None, alias="lastDone")

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class ExerciseCreate(ExerciseBase):
    pass

class ExerciseOut(ExerciseBase):
    user_id: int = Field(..., alias="userId")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,  # accepte snake_case et camelCase en entrÃ©e
    }
