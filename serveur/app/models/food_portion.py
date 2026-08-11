# app/models/food_portion.py
#
# Nutrition V1 (2026-06-12, cf. docs/NUTRITION_DESIGN.md §3.2).
# Portions nommees d'un aliment (« 1 oeuf = 60 g »). Ownership indirect :
# FoodPortion -> Food -> User (cascade ownership politique 8).

from sqlalchemy import Column, Integer, String, Float, ForeignKey, DateTime
from app.database import Base
import uuid


class FoodPortion(Base):
    __tablename__ = "food_portions"

    id = Column(Integer, primary_key=True, index=True)
    uuid = Column(String, nullable=False, unique=True, default=lambda: str(uuid.uuid4()))
    food_uuid = Column(String, ForeignKey("foods.uuid", ondelete="CASCADE"), nullable=False, index=True)

    label = Column(String, nullable=False)             # « 1 oeuf », « 1 cuillere a soupe » (user-typed)
    grams = Column(Float, nullable=False)              # equivalent en grammes

    updated_at = Column(DateTime(timezone=True), nullable=True)

    def __repr__(self):
        return (
            f"FoodPortion(id={self.id}, uuid='{self.uuid}', food_uuid='{self.food_uuid}', "
            f"label='{self.label}', grams={self.grams})"
        )
