from pydantic import BaseModel, Field
from typing import Optional
from app.utc_datetime import UTCDateTime

# -------------------- FoodPortion --------------------

class FoodPortionBase(BaseModel):
    food_uuid: str = Field(..., alias="foodUUID")
    label: str
    grams: float

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class FoodPortionCreate(FoodPortionBase):
    pass

class FoodPortionOut(FoodPortionBase):
    model_config = {
        "from_attributes": True,
        "populate_by_name": True,
    }
