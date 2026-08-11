# app/models/health_metric.py
#
# Santé / Health Connect V1 (2026-06-17). Type A user-scoped.
# Métriques passives génériques, vendor-agnostiques. Une row = une mesure.
# `type` UPPER_CASE (politique 11) : HEART_RATE / SLEEP / DISTANCE / ACTIVE_CALORIES.
# `unit` self-describing (bpm / min / m / km / kcal...) pour rester agnostique.
# Ancrage temporel : `date` (jour, requis, indexé) + `start_time` optionnel
# ("HH:MM") pour les mesures intraday (ex. échantillon de fréquence cardiaque).

from sqlalchemy import Column, Integer, String, Float, ForeignKey, DateTime
from app.database import Base
import uuid


class HealthMetric(Base):
    __tablename__ = "health_metrics"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    uuid = Column(String, nullable=False, unique=True, default=lambda: str(uuid.uuid4()))

    type = Column(String, nullable=False)               # UPPER_CASE : HEART_RATE | SLEEP | DISTANCE | ACTIVE_CALORIES
    value = Column(Float, nullable=False)
    unit = Column(String, nullable=False)               # bpm | min | m | km | kcal...
    date = Column(String, nullable=False, index=True)   # "YYYY-MM-DD" (convention projet)
    start_time = Column(String, nullable=True)          # "HH:MM" instant précis optionnel (mesures intraday)

    updated_at = Column(DateTime(timezone=True), nullable=True)

    def __repr__(self):
        return (
            f"HealthMetric(id={self.id}, user_id={self.user_id}, uuid='{self.uuid}', "
            f"type='{self.type}', value={self.value}, unit='{self.unit}', date='{self.date}')"
        )
