# app/models/quote.py
#
# Citations motivantes user-scoped (Type A, cascade ownership politique 8).
# Affichees aleatoirement sur le SplashScreen apres login + gerees depuis un
# ecran dedie cote Android. Pre-seedees au /signup via copy_starter_pack.

from sqlalchemy import Column, Integer, String, ForeignKey, DateTime
from app.database import Base
import uuid


class Quote(Base):
    __tablename__ = "quotes"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    uuid = Column(String, nullable=False, unique=True, default=lambda: str(uuid.uuid4()))

    text = Column(String, nullable=False)          # contenu de la citation (user-typed, non traduit)
    author = Column(String, nullable=True)         # auteur optionnel

    updated_at = Column(DateTime(timezone=True), nullable=True)

    def __repr__(self):
        return (
            f"Quote(id={self.id}, user_id={self.user_id}, uuid='{self.uuid}', "
            f"text='{self.text}', author={self.author}, updated_at={self.updated_at})"
        )
