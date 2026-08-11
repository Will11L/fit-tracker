# app/models/meal.py
#
# Nutrition V1 (2026-06-12, cf. docs/NUTRITION_DESIGN.md §3.4).
# Repas du journal quotidien. Type A user-scoped (cascade ownership politique 8).
# Cree seulement quand une premiere MealEntry y est ajoutee (les sections vides
# du journal viennent des meal_presets, pas de rows fantomes).
# date en String "YYYY-MM-DD" (convention projet : actual_workouts.date, tasks.due_date).
# order_index sans default (politique 10 : valeur positionnelle explicite).

from sqlalchemy import Column, Integer, String, ForeignKey, DateTime
from app.database import Base
import uuid


class Meal(Base):
    __tablename__ = "meals"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    uuid = Column(String, nullable=False, unique=True, default=lambda: str(uuid.uuid4()))

    date = Column(String, nullable=False, index=True)  # "YYYY-MM-DD" — jour du journal
    name = Column(String, nullable=False)              # libre, user-typed (« Petit-dej »...) — jamais traduit
    order_index = Column(Integer, nullable=False)      # ordre d'affichage dans la journee
    time = Column(String, nullable=True)               # "HH:MM" heure reelle de ce repas (facultative) ; surclasse le default_time du preset a l'affichage

    # Lien stable vers la periode (meal_preset) dont ce repas est issu. SET NULL :
    # le repas survit a la suppression du preset (il bascule alors en « ad hoc »).
    # nullable : repas ad hoc + repas legacy crees avant ce champ (appariement par nom).
    preset_uuid = Column(
        String, ForeignKey("meal_presets.uuid", ondelete="SET NULL"), nullable=True, index=True
    )

    updated_at = Column(DateTime(timezone=True), nullable=True)

    def __repr__(self):
        return (
            f"Meal(id={self.id}, user_id={self.user_id}, uuid='{self.uuid}', "
            f"date='{self.date}', name='{self.name}')"
        )
