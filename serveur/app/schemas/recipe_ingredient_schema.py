from pydantic import BaseModel, Field
from typing import Optional
from app.utc_datetime import UTCDateTime

# -------------------- RecipeIngredient --------------------

class RecipeIngredientBase(BaseModel):
    recipe_uuid: str = Field(..., alias="recipeUUID")
    food_uuid: str = Field(..., alias="foodUUID")
    quantity_g: float = Field(..., alias="quantityG")
    order_index: int = Field(..., alias="orderIndex")

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class RecipeIngredientCreate(RecipeIngredientBase):
    pass

class RecipeIngredientOut(RecipeIngredientBase):
    model_config = {
        "from_attributes": True,
        "populate_by_name": True,
    }
