# app/models/food.py
#
# Nutrition V1 (2026-06-12, cf. docs/NUTRITION_DESIGN.md §3.1).
# Catalogue d'aliments user-scoped (Type A, cascade ownership politique 8, D9 :
# le seed CIQUAL est copie au signup via starter_template — vague V2).
# source UPPER_CASE (politique 11) : CUSTOM / CIQUAL / OFF.

from sqlalchemy import Column, Integer, String, Float, Boolean, ForeignKey, DateTime
from app.database import Base
import uuid


class Food(Base):
    __tablename__ = "foods"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    uuid = Column(String, nullable=False, unique=True, default=lambda: str(uuid.uuid4()))

    name = Column(String, nullable=False)              # nom affiche (user-typed ou import, non traduit)
    brand = Column(String, nullable=True)              # marque (produits OFF)
    source = Column(String, nullable=False)            # CUSTOM | CIQUAL | OFF (pas de default : origine explicite)
    source_ref = Column(String, nullable=True)         # code CIQUAL ou barcode OFF (dedup a l'import)
    # Categorisation (feature Categories d'aliments) : groupe curate UPPER_CASE
    # (politique 11), ~18 valeurs (cf. app/food_taxonomy.py). Nullable : legacy +
    # mappe au mieux a l'import (CIQUAL/OFF) ou saisi a la creation custom. Le
    # regne (ANIMALE/VEGETALE/COMPLEMENT/AUTRE) n'est PAS stocke : derive via
    # food_taxonomy.realm_of().
    food_group = Column(String, nullable=True)

    kcal_per_100g = Column(Float, nullable=False)
    protein_per_100g = Column(Float, nullable=False)
    carbs_per_100g = Column(Float, nullable=False)
    fat_per_100g = Column(Float, nullable=False)
    # Micro-nutriments optionnels (D11) : dispo CIQUAL/OFF, affichage detail seulement
    fiber_per_100g = Column(Float, nullable=True)
    sugar_per_100g = Column(Float, nullable=True)
    sat_fat_per_100g = Column(Float, nullable=True)
    salt_per_100g = Column(Float, nullable=True)
    # Vitamines & mineraux (pack essentiel ~10, D11 etendu 2026-06-13). Nullable :
    # dispo partiellement selon la source (CIQUAL complet, OFF souvent partiel),
    # affichage info seulement (pas de cibles en v1). Mineraux en mg, vitamines en
    # mg (C) ou µg (D, B12, A=RAE).
    iron_per_100g = Column(Float, nullable=True)         # mg
    calcium_per_100g = Column(Float, nullable=True)      # mg
    magnesium_per_100g = Column(Float, nullable=True)    # mg
    zinc_per_100g = Column(Float, nullable=True)         # mg
    potassium_per_100g = Column(Float, nullable=True)    # mg
    sodium_per_100g = Column(Float, nullable=True)       # mg
    vitamin_c_per_100g = Column(Float, nullable=True)    # mg
    vitamin_d_per_100g = Column(Float, nullable=True)    # µg
    vitamin_b12_per_100g = Column(Float, nullable=True)  # µg
    vitamin_a_per_100g = Column(Float, nullable=True)    # µg RAE = retinol + beta-carotene/12

    is_favorite = Column(Boolean, default=False, nullable=False)   # cas majoritaire : pas favori
    archived = Column(Boolean, default=False, nullable=False)      # cas majoritaire : visible
    # Hydratation (2026-07-05) : marque un aliment "boisson eau" pour l'auto-comptage
    # d'hydratation (1 g = 1 ml). Posé à l'import OFF (categoriesTags contient
    # `en:waters`) ou coché manuellement. default=False : cas majoritaire (pas de l'eau).
    is_water = Column(Boolean, default=False, nullable=False)

    updated_at = Column(DateTime(timezone=True), nullable=True)

    def __repr__(self):
        return (
            f"Food(id={self.id}, user_id={self.user_id}, uuid='{self.uuid}', "
            f"name='{self.name}', source='{self.source}', kcal={self.kcal_per_100g})"
        )
