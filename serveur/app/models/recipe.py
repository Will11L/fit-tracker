# app/models/recipe.py
#
# Nutrition V1 (2026-06-12, cf. docs/NUTRITION_DESIGN.md §3.3).
# Plats composes ET repas enregistres (D7 : une seule entite couvre les deux).
# Type A user-scoped (cascade ownership politique 8).
# kind UPPER_CASE (politique 11) :
#   - RECIPE     : plat (macros au prorata du poids consomme, total_weight_g gere le ratio cru/cuit)
#   - SAVED_MEAL : repas enregistre (insertion des ingredients tels quels)

from sqlalchemy import Column, Integer, String, Float, ForeignKey, DateTime
from app.database import Base
import uuid


class Recipe(Base):
    __tablename__ = "recipes"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    uuid = Column(String, nullable=False, unique=True, default=lambda: str(uuid.uuid4()))

    name = Column(String, nullable=False)              # user-typed, non traduit
    kind = Column(String, nullable=False)              # RECIPE | SAVED_MEAL (pas de default : choix explicite)
    total_weight_g = Column(Float, nullable=True)      # poids final cuit (kind=RECIPE seulement)

    updated_at = Column(DateTime(timezone=True), nullable=True)

    def __repr__(self):
        return (
            f"Recipe(id={self.id}, user_id={self.user_id}, uuid='{self.uuid}', "
            f"name='{self.name}', kind='{self.kind}')"
        )
