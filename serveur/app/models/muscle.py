# Exemple : app/models/muscle.py

from sqlalchemy import Column, Integer, String, ForeignKey, Boolean, DateTime, func
from app.database import Base

import uuid

class Muscle(Base):
    __tablename__ = "muscles"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    
    name = Column(String, nullable=False)            # exemple: Triceps Long head (niveau précis)
    muscle_group = Column(String, nullable=True)     # exemple: Triceps (niveau intermédiaire)
    zone = Column(String, nullable=True)             # exemple: Arms (niveau haut)
    is_favorite = Column(Boolean, default=False)     # Indique si le muscle est favori ou non
    uuid = Column(String, nullable=False, default=lambda: str(uuid.uuid4()), unique=True)

    updated_at = Column(DateTime(timezone=True), nullable=True)

    def __repr__(self):
        return f"Muscle(id={self.id}, user_id={self.user_id}, name='{self.name}', muscle_group='{self.muscle_group}', zone='{self.zone}', is_favorite={self.is_favorite}, uuid='{self.uuid}', updated_at={self.updated_at})"