# app/models/task.py
#
# Phase 0 (2026-05-12) : modele unifie pour toutes les taches utilisateur.
# Remplace l'ancien `routine_task` (qui n'avait que les recurrentes quotidiennes
# par periode). Garde la table `routine_periods` intacte (table de reference
# legere Morning/Midday/Evening editable par user).
#
# Recurrence_kind UPPER_CASE (politique 11) :
#   - NONE    : tache one-off, due_date REQUIRED
#   - DAILY   : tache quotidienne, period_uuid REQUIRED, recurrence_start_date REQUIRED
#   - WEEKLY  : recurrence_weekdays REQUIRED (JSONB int[] [0,2,4]=Mon/Wed/Fri),
#               recurrence_start_date REQUIRED
#   - MONTHLY : meme jour du mois, derive de recurrence_start_date
#   - YEARLY  : meme date chaque annee, derive de recurrence_start_date
#
# Champs conditionnels (nullable au niveau DB, valides par Pydantic + validators) :
#   - due_date           valide ssi recurrence_kind=NONE
#   - period_uuid        valide ssi recurrence_kind=DAILY
#   - recurrence_weekdays valide ssi recurrence_kind=WEEKLY
#   - recurrence_start_date REQUIRED ssi recurrence_kind!=NONE
#   - recurrence_end_date OPTIONAL cap, valide ssi recurrence_kind!=NONE

from sqlalchemy import Column, Integer, String, ForeignKey, Boolean, DateTime
from sqlalchemy.dialects.postgresql import JSONB
from app.database import Base
import uuid


class Task(Base):
    __tablename__ = "tasks"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    uuid = Column(String, nullable=False, unique=True, default=lambda: str(uuid.uuid4()))

    # Core fields
    title = Column(String, nullable=False)
    notes = Column(String, nullable=True)
    is_active = Column(Boolean, nullable=False, default=True)
    # F7-1 : col Postgres `order_index` (mot-cle SQL `order` evite), attribut Python `order`
    order = Column("order_index", Integer, nullable=False, default=0)

    # Recurrence (REQUIRED, UPPER_CASE)
    # Default DAILY pour migration smooth depuis routine_tasks.
    recurrence_kind = Column(String, nullable=False, default="DAILY")

    # Conditional fields (nullable, valides par Pydantic + validators selon recurrence_kind)
    due_date = Column(String, nullable=True)               # YYYY-MM-DD, REQUIRED si NONE
    due_time = Column(String, nullable=True)               # HH:MM, optionnel (toujours)
    period_uuid = Column(
        String,
        ForeignKey("routine_periods.uuid", ondelete="SET NULL"),
        nullable=True,
        index=True,
    )                                                       # valide si DAILY
    recurrence_weekdays = Column(JSONB, nullable=True)     # [0..6] Mon=0..Sun=6, valide si WEEKLY
    recurrence_start_date = Column(String, nullable=True)  # YYYY-MM-DD, REQUIRED si != NONE
    recurrence_end_date = Column(String, nullable=True)    # YYYY-MM-DD, optional cap

    # B.4 (2026-05-12) : liste de dates ISO "YYYY-MM-DD" a exclure des occurrences
    # generees par ScheduledTaskExpander (pour mode "Only this" du dialog d'edit
    # de recurrence). Vide par defaut. Ignore pour recurrence_kind=NONE/DAILY.
    excluded_dates = Column(JSONB, nullable=False, default=list, server_default="[]")

    # Phase 3 (reminders)
    reminder_minutes_before = Column(Integer, nullable=True)

    updated_at = Column(DateTime(timezone=True), nullable=True)

    def __repr__(self):
        return (
            f"Task(id={self.id}, user_id={self.user_id}, uuid='{self.uuid}', "
            f"title='{self.title}', recurrence_kind='{self.recurrence_kind}', "
            f"period_uuid='{self.period_uuid}', due_date={self.due_date}, "
            f"updated_at={self.updated_at})"
        )
