# app/models/nutrition_goal.py
#
# Nutrition V1 (2026-06-12, cf. docs/NUTRITION_DESIGN.md §3.7).
# Cibles quotidiennes kcal + macros. Type A user-scoped.
# La cible active un jour J = celle avec le plus grand effective_from <= J
# (les stats passees comparent chaque jour a la cible active CE jour-la).
# day_kind UPPER_CASE (politique 11) : ALL en v1, extension future par type de
# jour (D3) sans migration. default=ALL legitime (politique 10 : cas majoritaire v1).
# Vue hebdo (D8) : pas d'entite dediee, le client compare somme(jours) a cible x 7.

from sqlalchemy import Column, Integer, String, Float, ForeignKey, DateTime
from app.database import Base
import uuid


class NutritionGoal(Base):
    __tablename__ = "nutrition_goals"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    uuid = Column(String, nullable=False, unique=True, default=lambda: str(uuid.uuid4()))

    effective_from = Column(String, nullable=False, index=True)  # "YYYY-MM-DD" (convention projet)
    day_kind = Column(String, nullable=False, default="ALL")     # ALL (v1) ; extension future D3

    kcal = Column(Float, nullable=False)
    protein_g = Column(Float, nullable=False)
    carbs_g = Column(Float, nullable=False)
    fat_g = Column(Float, nullable=False)

    updated_at = Column(DateTime(timezone=True), nullable=True)

    def __repr__(self):
        return (
            f"NutritionGoal(id={self.id}, user_id={self.user_id}, uuid='{self.uuid}', "
            f"effective_from='{self.effective_from}', kcal={self.kcal})"
        )
