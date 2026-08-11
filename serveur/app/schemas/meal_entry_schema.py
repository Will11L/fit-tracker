from pydantic import BaseModel, Field
from typing import Optional
from app.utc_datetime import UTCDateTime

# -------------------- MealEntry --------------------
# Snapshot D5 : macros per-100g figees a l'ajout. food/recipe = refs informatives
# (SET NULL en DB), nullables aussi sur le wire.

class MealEntryBase(BaseModel):
    meal_uuid: str = Field(..., alias="mealUUID")
    food_uuid: Optional[str] = Field(None, alias="foodUUID")
    recipe_uuid: Optional[str] = Field(None, alias="recipeUUID")

    display_name: str = Field(..., alias="displayName")
    quantity_g: float = Field(..., alias="quantityG")
    portion_label: Optional[str] = Field(None, alias="portionLabel")

    kcal_per_100g: float = Field(..., alias="kcalPer100g")
    protein_per_100g: float = Field(..., alias="proteinPer100g")
    carbs_per_100g: float = Field(..., alias="carbsPer100g")
    fat_per_100g: float = Field(..., alias="fatPer100g")
    fiber_per_100g: Optional[float] = Field(None, alias="fiberPer100g")
    sugar_per_100g: Optional[float] = Field(None, alias="sugarPer100g")
    sat_fat_per_100g: Optional[float] = Field(None, alias="satFatPer100g")
    salt_per_100g: Optional[float] = Field(None, alias="saltPer100g")
    # Snapshot vitamines & mineraux (pack essentiel ~10, D11 etendu)
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

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class MealEntryCreate(MealEntryBase):
    pass

class MealEntryOut(MealEntryBase):
    model_config = {
        "from_attributes": True,
        "populate_by_name": True,
    }
