from sqlalchemy import Column, Integer, String, ForeignKey, DateTime
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.sql import func
from app.database import Base

import uuid


class Notification(Base):
    __tablename__ = "notifications"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)

    uuid = Column(String, default=lambda: str(uuid.uuid4()), nullable=False, unique=True)

    # Type & affichage
    type = Column(String, nullable=False)          # ROUTINE_PERIOD_START, TIMER_DONE, etc.
    level = Column(String, nullable=False, default="info")  # info | success | warning | error

    title = Column(String, nullable=False)
    body = Column(String, nullable=True)

    # Payload libre
    data = Column(JSONB, nullable=True)

    # DÃ©duplication
    dedupe_key = Column(String, nullable=True)

    # Statuts
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    read_at = Column(DateTime(timezone=True), nullable=True)
    archived_at = Column(DateTime(timezone=True), nullable=True)

    updated_at = Column(DateTime(timezone=True), nullable=True)
