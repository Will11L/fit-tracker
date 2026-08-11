# app/models/meal_preset.py
#
# Nutrition V1 (2026-06-12, cf. docs/NUTRITION_DESIGN.md §3.5, D10).
# Periodes habituelles du journal (« Petit-dej », « Dejeuner »...), gerees par
# l'utilisateur. Type A user-scoped. Le journal pre-affiche ces periodes chaque
# jour comme sections vides — un Meal (row) n'est cree qu'a la premiere entry.
# Seed au signup : 4 presets par defaut (vague V2).
# order_index sans default (politique 10 : valeur positionnelle explicite).

from sqlalchemy import Column, Integer, String, ForeignKey, DateTime
from app.database import Base
import uuid


class MealPreset(Base):
    __tablename__ = "meal_presets"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    uuid = Column(String, nullable=False, unique=True, default=lambda: str(uuid.uuid4()))

    name = Column(String, nullable=False)              # user-typed, non traduit
    order_index = Column(Integer, nullable=False)      # ordre des sections du journal
    default_time = Column(String, nullable=True)       # "HH:MM" indicatif (convention projet : time en String)

    updated_at = Column(DateTime(timezone=True), nullable=True)

    def __repr__(self):
        return (
            f"MealPreset(id={self.id}, user_id={self.user_id}, uuid='{self.uuid}', "
            f"name='{self.name}', order_index={self.order_index})"
        )
