from sqlalchemy import Column, Integer, ForeignKey, String, DateTime, func
from app.database import Base

import uuid

class ExerciseEquipment(Base):
    __tablename__ = "exercise_equipment"
    
    id = Column(Integer, primary_key=True, index=True)
    uuid = Column(String, nullable=False, default=lambda: str(uuid.uuid4()), unique=True)

    updated_at = Column(DateTime(timezone=True), nullable=True)

    exercise_uuid = Column(String, ForeignKey("exercises.uuid", ondelete="CASCADE"), nullable=False, index=True)
    equipment_uuid = Column(String, ForeignKey("equipments.uuid", ondelete="CASCADE"), nullable=False, index=True)
