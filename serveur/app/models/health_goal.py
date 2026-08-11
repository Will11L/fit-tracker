# app/models/health_goal.py
#
# Santé / Health Connect V1 (2026-06-17). Type A user-scoped.
# Objectif santé versionné dans le temps (même sémantique que nutrition_goal).
# L'objectif actif d'un `type` un jour J = celui avec le plus grand
# effective_from <= J. `type` UPPER_CASE (politique 11) : STEPS en v1,
# extensible (DISTANCE, ACTIVE_CALORIES...) sans migration.

from sqlalchemy import Column, Integer, String, Float, ForeignKey, DateTime
from app.database import Base
import uuid


class HealthGoal(Base):
    __tablename__ = "health_goals"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    uuid = Column(String, nullable=False, unique=True, default=lambda: str(uuid.uuid4()))

    type = Column(String, nullable=False)                    # UPPER_CASE : STEPS (v1), extensible
    target = Column(Float, nullable=False)
    effective_from = Column(String, nullable=False, index=True)  # "YYYY-MM-DD"

    updated_at = Column(DateTime(timezone=True), nullable=True)

    def __repr__(self):
        return (
            f"HealthGoal(id={self.id}, user_id={self.user_id}, uuid='{self.uuid}', "
            f"type='{self.type}', target={self.target}, effective_from='{self.effective_from}')"
        )
