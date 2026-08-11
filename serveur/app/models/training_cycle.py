from sqlalchemy import Column, Integer, String, Date, DateTime, ForeignKey, func
from app.database import Base

import uuid

class TrainingCycle(Base):
    __tablename__ = "training_cycles"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    name = Column(String, nullable=False)
    start_date = Column(Date, nullable=False)
    end_date = Column(Date, nullable=False)

    updated_at = Column(DateTime(timezone=True), nullable=True)

    uuid = Column(String, default=lambda: str(uuid.uuid4()), nullable=False, unique=True)
