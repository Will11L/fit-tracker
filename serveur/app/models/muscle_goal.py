from sqlalchemy import Column, Integer, String, Boolean, ForeignKey, DateTime, func
from app.database import Base
import uuid

class MuscleGoal(Base):
    __tablename__ = "muscle_goals"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    muscle_uuid = Column(String, ForeignKey("muscles.uuid", ondelete="CASCADE"), nullable=False, index=True)

    uuid = Column(String, nullable=False, default=lambda: str(uuid.uuid4()), unique=True)

    priority = Column(String, nullable=False)
    done = Column(Integer, default=0, nullable=False)
    target = Column(String, nullable=False)
    week_iso = Column(String, nullable=False)
    status = Column(String, default="IN_PROGRESS", nullable=False)
    added_manually = Column(Boolean, default=False, nullable=False)

    updated_at = Column(DateTime(timezone=True), nullable=True)
