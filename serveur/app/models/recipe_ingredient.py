# app/models/recipe_ingredient.py
#
# Nutrition V1 (2026-06-12, cf. docs/NUTRITION_DESIGN.md §3.3).
# Ingredient d'une recette. Ownership indirect : RecipeIngredient -> Recipe -> User.
# food_uuid = reference VIVANTE (pas de snapshot : une recette est un modele,
# pas de l'historique) -> CASCADE si le Food est supprime.
# order_index sans default (politique 10 : valeur positionnelle explicite).

from sqlalchemy import Column, Integer, String, Float, ForeignKey, DateTime
from app.database import Base
import uuid


class RecipeIngredient(Base):
    __tablename__ = "recipe_ingredients"

    id = Column(Integer, primary_key=True, index=True)
    uuid = Column(String, nullable=False, unique=True, default=lambda: str(uuid.uuid4()))
    recipe_uuid = Column(String, ForeignKey("recipes.uuid", ondelete="CASCADE"), nullable=False, index=True)
    food_uuid = Column(String, ForeignKey("foods.uuid", ondelete="CASCADE"), nullable=False, index=True)

    quantity_g = Column(Float, nullable=False)
    order_index = Column(Integer, nullable=False)      # pas de default (politique 10)

    updated_at = Column(DateTime(timezone=True), nullable=True)

    def __repr__(self):
        return (
            f"RecipeIngredient(id={self.id}, uuid='{self.uuid}', recipe_uuid='{self.recipe_uuid}', "
            f"food_uuid='{self.food_uuid}', quantity_g={self.quantity_g})"
        )
