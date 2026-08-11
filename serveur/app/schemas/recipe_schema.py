from pydantic import BaseModel, Field
from typing import Optional
from app.utc_datetime import UTCDateTime

# -------------------- Recipe --------------------

class RecipeBase(BaseModel):
    name: str
    kind: str                                            # RECIPE | SAVED_MEAL (politique 11)
    total_weight_g: Optional[float] = Field(None, alias="totalWeightG")

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class RecipeCreate(RecipeBase):
    pass

class RecipeOut(RecipeBase):
    user_id: int = Field(..., alias="userId")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,
    }
