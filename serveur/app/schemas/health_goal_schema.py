from pydantic import BaseModel, Field
from typing import Optional
from app.utc_datetime import UTCDateTime

# -------------------- HealthGoal --------------------

class HealthGoalBase(BaseModel):
    type: str                                                # UPPER_CASE : STEPS (v1), extensible
    target: float
    effective_from: str = Field(..., alias="effectiveFrom")  # "YYYY-MM-DD"

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class HealthGoalCreate(HealthGoalBase):
    pass

class HealthGoalOut(HealthGoalBase):
    user_id: int = Field(..., alias="userId")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,
    }
