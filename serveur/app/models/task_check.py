# app/models/task_check.py
#
# Phase 0 (2026-05-12) : remplace `routine_task_check`. Une coche par
# occurrence_date d'une tache (one-off : 1 seule occurrence; recurrente :
# 1 par jour ou par cycle).
#
# UniqueConstraint (user_id, task_uuid, occurrence_date) : 1 seule coche
# par jour par tache (idempotent en cas de re-PUT).

from sqlalchemy import Column, Integer, String, ForeignKey, Boolean, DateTime, UniqueConstraint
from app.database import Base
import uuid


class TaskCheck(Base):
    __tablename__ = "task_checks"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    task_uuid = Column(
        String,
        ForeignKey("tasks.uuid", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    occurrence_date = Column(String, nullable=False)   # YYYY-MM-DD

    is_checked = Column(Boolean, nullable=False, default=False)
    checked_at = Column(DateTime(timezone=True), nullable=True)

    uuid = Column(String, nullable=False, unique=True, default=lambda: str(uuid.uuid4()))
    updated_at = Column(DateTime(timezone=True), nullable=True)

    __table_args__ = (
        UniqueConstraint("user_id", "task_uuid", "occurrence_date", name="uq_task_check_per_day"),
    )

    def __repr__(self):
        return (
            f"TaskCheck(id={self.id}, user_id={self.user_id}, "
            f"task_uuid='{self.task_uuid}', occurrence_date={self.occurrence_date}, "
            f"is_checked={self.is_checked}, checked_at={self.checked_at}, "
            f"uuid='{self.uuid}', updated_at={self.updated_at})"
        )
