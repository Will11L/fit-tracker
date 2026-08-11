from pydantic import BaseModel, Field
from typing import Optional
from app.utc_datetime import UTCDateTime

# -------------------- NutritionGoal --------------------

class NutritionGoalBase(BaseModel):
    effective_from: str = Field(..., alias="effectiveFrom")  # "YYYY-MM-DD"
    day_kind: str = Field("ALL", alias="dayKind")            # ALL (v1, politique 11)

    kcal: float
    protein_g: float = Field(..., alias="proteinG")
    carbs_g: float = Field(..., alias="carbsG")
    fat_g: float = Field(..., alias="fatG")

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class NutritionGoalCreate(NutritionGoalBase):
    pass

class NutritionGoalOut(NutritionGoalBase):
    user_id: int = Field(..., alias="userId")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,
    }
