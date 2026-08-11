# app/models/meal_entry.py
#
# Nutrition V1 (2026-06-12, cf. docs/NUTRITION_DESIGN.md §3.6) — table centrale.
# Une consommation dans un repas. Ownership indirect : MealEntry -> Meal -> User.
#
# Snapshot D5 : les macros per-100g + display_name sont FIGES au moment de
# l'ajout — l'historique est immuable, corriger/supprimer un aliment du
# catalogue ne reecrit pas le passe. FK food/recipe en SET NULL : l'entry
# survit a la suppression de sa source grace au snapshot.
# Totaux derives cote client : total = per_100g x quantity_g / 100.

from sqlalchemy import Column, Integer, String, Float, ForeignKey, DateTime
from app.database import Base
import uuid


class MealEntry(Base):
    __tablename__ = "meal_entries"

    id = Column(Integer, primary_key=True, index=True)
    uuid = Column(String, nullable=False, unique=True, default=lambda: str(uuid.uuid4()))
    meal_uuid = Column(String, ForeignKey("meals.uuid", ondelete="CASCADE"), nullable=False, index=True)
    # References informatives (SET NULL : le snapshot rend l'entry autonome)
    food_uuid = Column(String, ForeignKey("foods.uuid", ondelete="SET NULL"), nullable=True, index=True)
    recipe_uuid = Column(String, ForeignKey("recipes.uuid", ondelete="SET NULL"), nullable=True, index=True)

    display_name = Column(String, nullable=False)      # snapshot du nom (lisible meme si Food supprime/renomme)
    quantity_g = Column(Float, nullable=False)         # quantite consommee en g
    portion_label = Column(String, nullable=True)      # snapshot du label de portion (« 2 oeufs »)

    # Snapshot macros per-100g (D5)
    kcal_per_100g = Column(Float, nullable=False)
    protein_per_100g = Column(Float, nullable=False)
    carbs_per_100g = Column(Float, nullable=False)
    fat_per_100g = Column(Float, nullable=False)
    # Snapshot micro-nutriments optionnels (D11)
    fiber_per_100g = Column(Float, nullable=True)
    sugar_per_100g = Column(Float, nullable=True)
    sat_fat_per_100g = Column(Float, nullable=True)
    salt_per_100g = Column(Float, nullable=True)
    # Snapshot vitamines & mineraux (pack essentiel ~10, D11 etendu 2026-06-13).
    iron_per_100g = Column(Float, nullable=True)         # mg
    calcium_per_100g = Column(Float, nullable=True)      # mg
    magnesium_per_100g = Column(Float, nullable=True)    # mg
    zinc_per_100g = Column(Float, nullable=True)         # mg
    potassium_per_100g = Column(Float, nullable=True)    # mg
    sodium_per_100g = Column(Float, nullable=True)       # mg
    vitamin_c_per_100g = Column(Float, nullable=True)    # mg
    vitamin_d_per_100g = Column(Float, nullable=True)    # µg
    vitamin_b12_per_100g = Column(Float, nullable=True)  # µg
    vitamin_a_per_100g = Column(Float, nullable=True)    # µg RAE

    updated_at = Column(DateTime(timezone=True), nullable=True)

    def __repr__(self):
        return (
            f"MealEntry(id={self.id}, uuid='{self.uuid}', meal_uuid='{self.meal_uuid}', "
            f"display_name='{self.display_name}', quantity_g={self.quantity_g})"
        )
