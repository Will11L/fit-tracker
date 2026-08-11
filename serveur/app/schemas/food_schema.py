from pydantic import BaseModel, Field
from typing import Optional
from app.utc_datetime import UTCDateTime

# -------------------- Food --------------------

class FoodBase(BaseModel):
    name: str
    brand: Optional[str] = None
    source: str                                          # CUSTOM | CIQUAL | OFF (politique 11)
    source_ref: Optional[str] = Field(None, alias="sourceRef")
    food_group: Optional[str] = Field(None, alias="foodGroup")  # groupe curate UPPER_CASE (politique 11), nullable

    kcal_per_100g: float = Field(..., alias="kcalPer100g")
    protein_per_100g: float = Field(..., alias="proteinPer100g")
    carbs_per_100g: float = Field(..., alias="carbsPer100g")
    fat_per_100g: float = Field(..., alias="fatPer100g")
    fiber_per_100g: Optional[float] = Field(None, alias="fiberPer100g")
    sugar_per_100g: Optional[float] = Field(None, alias="sugarPer100g")
    sat_fat_per_100g: Optional[float] = Field(None, alias="satFatPer100g")
    salt_per_100g: Optional[float] = Field(None, alias="saltPer100g")
    # Vitamines & mineraux (pack essentiel ~10, D11 etendu)
    iron_per_100g: Optional[float] = Field(None, alias="ironPer100g")
    calcium_per_100g: Optional[float] = Field(None, alias="calciumPer100g")
    magnesium_per_100g: Optional[float] = Field(None, alias="magnesiumPer100g")
    zinc_per_100g: Optional[float] = Field(None, alias="zincPer100g")
    potassium_per_100g: Optional[float] = Field(None, alias="potassiumPer100g")
    sodium_per_100g: Optional[float] = Field(None, alias="sodiumPer100g")
    vitamin_c_per_100g: Optional[float] = Field(None, alias="vitaminCPer100g")
    vitamin_d_per_100g: Optional[float] = Field(None, alias="vitaminDPer100g")
    vitamin_b12_per_100g: Optional[float] = Field(None, alias="vitaminB12Per100g")
    vitamin_a_per_100g: Optional[float] = Field(None, alias="vitaminAPer100g")

    is_favorite: bool = Field(False, alias="isFavorite")
    archived: bool = False
    is_water: bool = Field(False, alias="isWater")   # boisson eau → auto-comptage hydratation (1 g = 1 ml)

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class FoodCreate(FoodBase):
    pass

class FoodOut(FoodBase):
    user_id: int = Field(..., alias="userId")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,
    }
