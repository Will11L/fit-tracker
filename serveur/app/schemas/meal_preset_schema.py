from pydantic import BaseModel, Field
from typing import Optional
from app.utc_datetime import UTCDateTime

# -------------------- MealPreset --------------------

class MealPresetBase(BaseModel):
    name: str
    order_index: int = Field(..., alias="orderIndex")
    default_time: Optional[str] = Field(None, alias="defaultTime")   # "HH:MM"

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class MealPresetCreate(MealPresetBase):
    pass

class MealPresetOut(MealPresetBase):
    user_id: int = Field(..., alias="userId")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,
    }
