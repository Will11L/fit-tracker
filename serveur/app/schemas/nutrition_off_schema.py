from pydantic import BaseModel, Field
from typing import Optional

# -------------------- Open Food Facts proxy (Nutrition V2) --------------------
#
# Produit OFF normalise per-100g par le proxy serveur (docs/NUTRITION_DESIGN.md
# §4.1). Memes noms de champs que Food (snake_case + alias camelCase,
# politique 17) pour que le client copie directement vers son catalogue
# (source=OFF, source_ref=barcode). serving_size/serving_quantity_g alimentent
# food_portions cote client quand disponibles.

class OffProductOut(BaseModel):
    source_ref: str = Field(..., alias="sourceRef")            # barcode
    name: str
    brand: Optional[str] = None

    kcal_per_100g: float = Field(..., alias="kcalPer100g")
    protein_per_100g: float = Field(..., alias="proteinPer100g")
    carbs_per_100g: float = Field(..., alias="carbsPer100g")
    fat_per_100g: float = Field(..., alias="fatPer100g")
    fiber_per_100g: Optional[float] = Field(None, alias="fiberPer100g")
    sugar_per_100g: Optional[float] = Field(None, alias="sugarPer100g")
    sat_fat_per_100g: Optional[float] = Field(None, alias="satFatPer100g")
    salt_per_100g: Optional[float] = Field(None, alias="saltPer100g")
    # Vitamines & mineraux (pack essentiel ~10, D11 etendu) — souvent partiels cote OFF
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

    serving_size: Optional[str] = Field(None, alias="servingSize")
    serving_quantity_g: Optional[float] = Field(None, alias="servingQuantityG")
    # Categories OFF brutes (tags slugifies `en:`/`fr:`…) — le client les mappe vers un groupe
    # curate a l'import (food-category.ts). Souvent partiel/absent cote OFF -> liste vide.
    categories_tags: list[str] = Field(default_factory=list, alias="categoriesTags")

    model_config = {"populate_by_name": True}
