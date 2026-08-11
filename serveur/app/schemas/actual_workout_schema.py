from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime
from app.utc_datetime import UTCDateTime

# -------------------- ActualWorkout --------------------

class ActualWorkoutBase(BaseModel):
    name: str
    date: str = Field(..., pattern=r"^\d{4}-\d{2}-\d{2}$")  # format ISO strict : YYYY-MM-DD
    notes: Optional[str] = None
    location: Optional[str] = None
    is_done: bool = Field(..., alias="isDone")

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str

    model_config = {
        "populate_by_name": True,  # accepte camelCase et snake_case en entrÃ©e
    }

class ActualWorkoutCreate(ActualWorkoutBase):
    pass

class ActualWorkoutOut(ActualWorkoutBase):
    user_id: int = Field(..., alias="userId")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,  # accepte camelCase et snake_case en entrÃ©e
    }
