# app/models/water_intake.py
#
# Hydratation (2026-07-05). Type A user-scoped.
# Chaque row = une prise d'eau horodatée (un verre / une bouteille). Le total du
# jour se dérive côté client par SUM(amount_ml) sur la date. Le serveur stocke
# les événements bruts, il ne calcule aucun agrégat (décision produit 2026-07-05).
# `date` = jour local "YYYY-MM-DD" (regroupement) ; `created_at` = instant de la
# prise (heure de la journée). Objectif journalier versionné via health_goals
# (type WATER_ML), pas d'entité dédiée.

from sqlalchemy import Column, Integer, String, ForeignKey, DateTime
from app.database import Base
import uuid


class WaterIntake(Base):
    __tablename__ = "water_intakes"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    uuid = Column(String, nullable=False, unique=True, default=lambda: str(uuid.uuid4()))

    date = Column(String, nullable=False, index=True)   # "YYYY-MM-DD" (jour local, convention projet)
    amount_ml = Column(Integer, nullable=False)          # volume d'une prise en ml (> 0)

    created_at = Column(DateTime(timezone=True), nullable=True)   # instant de la prise
    updated_at = Column(DateTime(timezone=True), nullable=True)

    def __repr__(self):
        return (
            f"WaterIntake(id={self.id}, user_id={self.user_id}, uuid='{self.uuid}', "
            f"date='{self.date}', amount_ml={self.amount_ml})"
        )
