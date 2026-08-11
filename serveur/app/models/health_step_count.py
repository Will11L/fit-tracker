# app/models/health_step_count.py
#
# Santé / Health Connect V1 (2026-06-17). Type A user-scoped.
# Compteur de pas en buckets intraday : chaque row = une tranche de la journée
# (ex. tranche horaire). Le total quotidien se dérive par SUM(steps) sur la date.
# Le bucketing permet le near-real-time : le client ré-upsert le bucket courant
# au fil de la journée (uuid stable par user+date+bucket) sans attendre la fin de
# journée. Granularité (tranche) décidée par le client, pas figée en base.

from sqlalchemy import Column, Integer, String, ForeignKey, DateTime
from app.database import Base
import uuid


class HealthStepCount(Base):
    __tablename__ = "health_step_counts"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    uuid = Column(String, nullable=False, unique=True, default=lambda: str(uuid.uuid4()))

    date = Column(String, nullable=False, index=True)   # "YYYY-MM-DD" (convention projet)
    bucket_start = Column(String, nullable=False)        # "HH:MM" début de la tranche intraday
    steps = Column(Integer, nullable=False)

    updated_at = Column(DateTime(timezone=True), nullable=True)

    def __repr__(self):
        return (
            f"HealthStepCount(id={self.id}, user_id={self.user_id}, uuid='{self.uuid}', "
            f"date='{self.date}', bucket_start='{self.bucket_start}', steps={self.steps})"
        )
