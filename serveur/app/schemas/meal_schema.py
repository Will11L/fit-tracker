from pydantic import BaseModel, Field
from typing import Optional
from app.utc_datetime import UTCDateTime

# -------------------- Meal --------------------

class MealBase(BaseModel):
    date: str                                            # "YYYY-MM-DD"
    name: str
    order_index: int = Field(..., alias="orderIndex")
    time: Optional[str] = None                           # "HH:MM" heure reelle du repas (facultative)
    preset_uuid: Optional[str] = Field(None, alias="presetUuid")  # FK meal_presets.uuid (lien stable)

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class MealCreate(MealBase):
    pass

class MealOut(MealBase):
    user_id: int = Field(..., alias="userId")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,
    }
