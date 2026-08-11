# app/models/routine_period.py

from sqlalchemy import Column, Integer, String, ForeignKey, DateTime, Time
from app.database import Base
import uuid

class RoutinePeriod(Base):
    __tablename__ = "routine_periods"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)

    name = Column(String, nullable=False)                 # ex: "Matin"
    start_time = Column(String, nullable=False)             # ex: 06:30
    end_time = Column(String, nullable=False)               # ex: 09:00
    # F7-1 : col Postgres `order_index` (mot-clé SQL `order` évité), attribut Python `order` (cohérent Pydantic + Room)
    order = Column("order_index", Integer, nullable=False, default=0)    # ordre d'affichage

    # Rappels notifs (2026-06-08) : minutes avant le début / la fin de la période.
    # Convention : NULL = rappel désactivé, 0 = pile à l'heure, N = N min avant.
    # Indépendants (et/ou). Pas de default serveur (#10) : le backfill des périodes
    # existantes (= rappel de début pile à l'heure) est fait par la migration Alembic.
    reminder_before_start_minutes = Column(Integer, nullable=True)
    reminder_before_end_minutes = Column(Integer, nullable=True)

    uuid = Column(String, nullable=False, unique=True, default=lambda: str(uuid.uuid4()))

    updated_at = Column(DateTime(timezone=True), nullable=True)

    def __repr__(self):
        return (
            f"RoutinePeriod(id={self.id}, user_id={self.user_id}, "
            f"name='{self.name}', start_time={self.start_time}, end_time={self.end_time}, "
            f"order={self.order}, "
            f"reminder_before_start_minutes={self.reminder_before_start_minutes}, "
            f"reminder_before_end_minutes={self.reminder_before_end_minutes}, "
            f"uuid='{self.uuid}', updated_at={self.updated_at})"
        )
