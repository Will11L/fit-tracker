# app/routers/nutrition_off_router.py
#
# Nutrition V2 (2026-06-12, cf. docs/NUTRITION_DESIGN.md §4.1).
# Proxy serveur vers l'API Open Food Facts : les clients (web + Android futur)
# ne connaissent qu'un seul format (Food per-100g), le User-Agent conforme aux
# CGU OFF est pose une fois ici, cache TTL en memoire pour limiter les appels
# sortants, pas de CORS cote client.
#
# Divergence assumee du squelette canonique CRUD (politique 9) : ce router est
# un proxy read-only vers une API externe, pas une entite persistee — pas de
# CRUD/schema Create associes. Auth JWT obligatoire (politique 8 : aucun
# endpoint public).

from __future__ import annotations

import asyncio
import logging
import time

import httpx
from fastapi import APIRouter, Depends, HTTPException, Query

from app.dependencies import get_current_user_id
from app.schemas.nutrition_off_schema import OffProductOut

log = logging.getLogger(__name__)

nutrition_off_router = APIRouter(tags=["nutrition-off"])

OFF_BASE_URL = "https://world.openfoodfacts.org"
# API de recherche moderne (Search-a-licious) : rapide et fiable, la ou
# cgi/search.pl (legacy) renvoie des 503 sous charge (panne observee en prod
# 2026-06-12, >10 min). On cherche via SaL avec fallback legacy.
OFF_SEARCH_URL = "https://search.openfoodfacts.org/search"
# Format requis par OFF : AppName/Version (contact). Cf. CGU API OFF.
OFF_USER_AGENT = "sport-app/1.0 (https://github.com/Will11L/sport-app; will90.lehnert@gmail.com)"
_OFF_TIMEOUT_SECONDS = 10.0
# Champs demandes a OFF (limite la taille des payloads).
# nutriments contient deja tous les sous-champs (iron_100g, calcium_100g,
# vitamin-c_100g…) -> pas besoin de les lister un par un.
_OFF_FIELDS = "code,product_name,brands,nutriments,serving_size,serving_quantity,categories_tags"

# -------------------- Cache TTL en memoire --------------------
# Suffisant a l'echelle de l'app (poignee d'users, process unique sur la Pi).
_SEARCH_TTL_SECONDS = 3600.0       # 1 h : les resultats de recherche bougent peu
_PRODUCT_TTL_SECONDS = 86400.0     # 24 h : un produit code-barres est tres stable
_CACHE_MAX_ENTRIES = 512

_cache: dict[str, tuple[float, object]] = {}


def _cache_get(key: str):
    entry = _cache.get(key)
    if entry is None:
        return None
    expires_at, value = entry
    if time.monotonic() >= expires_at:
        _cache.pop(key, None)
        return None
    return value


def _cache_set(key: str, value, ttl: float) -> None:
    if len(_cache) >= _CACHE_MAX_ENTRIES:
        # Eviction simple : purge les expires, puis le plus proche de l'expiration.
        now = time.monotonic()
        for k in [k for k, (exp, _) in _cache.items() if exp <= now]:
            _cache.pop(k, None)
        if len(_cache) >= _CACHE_MAX_ENTRIES:
            oldest = min(_cache, key=lambda k: _cache[k][0])
            _cache.pop(oldest, None)
    _cache[key] = (time.monotonic() + ttl, value)


# -------------------- Appel OFF + normalisation --------------------

async def _fetch_json(url: str, params: dict | None = None) -> dict:
    """GET JSON vers OFF. Monkeypatchable dans les tests. 502 si OFF down."""
    try:
        async with httpx.AsyncClient(
            timeout=_OFF_TIMEOUT_SECONDS,
            headers={"User-Agent": OFF_USER_AGENT},
            follow_redirects=True,
        ) as client:
            resp = await client.get(url, params=params)
            resp.raise_for_status()
            return resp.json()
    except httpx.HTTPError as exc:
        log.warning("Open Food Facts unreachable: %s", exc)
        raise HTTPException(status_code=502, detail="Open Food Facts injoignable") from exc


async def _fetch_json_retry(url: str, params: dict | None = None) -> dict:
    """_fetch_json avec 1 retry : l'API search legacy d'OFF (cgi/search.pl)
    renvoie des 503 transitoires sous charge (observe en prod 2026-06-12,
    feedback Functional review V4) — un 2e essai absorbe la plupart."""
    try:
        return await _fetch_json(url, params)
    except HTTPException:
        await asyncio.sleep(0.5)
        return await _fetch_json(url, params)


def _to_float(value) -> float | None:
    if value is None or value == "":
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


# OFF normalise les champs `<nutriment>_100g` en GRAMMES (base SI, comme
# salt_100g deja utilise tel quel). Notre schema stocke les mineraux + la
# vitamine C en mg, et les vitamines D / B12 / A en µg -> conversion depuis g.
def _mg_from_off(value) -> float | None:
    """g (OFF) -> mg. Arrondi pour limiter le bruit flottant. None si absent."""
    grams = _to_float(value)
    return round(grams * 1000.0, 4) if grams is not None else None


def _ug_from_off(value) -> float | None:
    """g (OFF) -> µg. None si absent."""
    grams = _to_float(value)
    return round(grams * 1_000_000.0, 4) if grams is not None else None


def normalize_off_product(product: dict, *, allow_missing_energy: bool = False) -> dict | None:
    """Normalise un produit OFF brut vers le format Food per-100g.

    Retourne None si le produit est inexploitable (pas de nom/code). kcal
    prioritaire ; fallback conversion kJ -> kcal (/ 4.184). Macros NOT NULL
    manquantes -> 0.0 ; micro-nutriments manquants -> None (D11).

    [allow_missing_energy] : quand un produit n'a AUCUN champ énergie, la
    recherche le filtre (bruit) mais un lookup par code-barres le garde en
    défaultant kcal à 0.0 — une eau minérale a 0 kcal légitime et doit rester
    importable (feedback scan 2026-07-05 : Wattwiller rejeté à tort en 404).
    """
    code = str(product.get("code") or "").strip()
    name = str(product.get("product_name") or "").strip()
    if not code or not name:
        return None

    nutriments = product.get("nutriments") or {}
    kcal = _to_float(nutriments.get("energy-kcal_100g"))
    if kcal is None:
        kj = _to_float(nutriments.get("energy-kj_100g")) or _to_float(nutriments.get("energy_100g"))
        kcal = round(kj / 4.184, 1) if kj is not None else None
    if kcal is None:
        if not allow_missing_energy:
            return None
        kcal = 0.0

    brands_raw = product.get("brands")
    if isinstance(brands_raw, list):  # Search-a-licious : brands en tableau
        brands_raw = ",".join(str(b) for b in brands_raw)
    brand = str(brands_raw or "").split(",")[0].strip() or None
    return {
        "source_ref": code,
        "name": name,
        "brand": brand,
        "kcal_per_100g": kcal,
        "protein_per_100g": _to_float(nutriments.get("proteins_100g")) or 0.0,
        "carbs_per_100g": _to_float(nutriments.get("carbohydrates_100g")) or 0.0,
        "fat_per_100g": _to_float(nutriments.get("fat_100g")) or 0.0,
        "fiber_per_100g": _to_float(nutriments.get("fiber_100g")),
        "sugar_per_100g": _to_float(nutriments.get("sugars_100g")),
        "sat_fat_per_100g": _to_float(nutriments.get("saturated-fat_100g")),
        "salt_per_100g": _to_float(nutriments.get("salt_100g")),
        # Vitamines & mineraux (pack essentiel ~10, D11 etendu) : OFF normalise en
        # g/100g pour les mineraux -> conversion mg ; vitamines deja en mg/µg.
        # Souvent partiels selon le produit -> None si absent (colonnes nullable).
        "iron_per_100g": _mg_from_off(nutriments.get("iron_100g")),
        "calcium_per_100g": _mg_from_off(nutriments.get("calcium_100g")),
        "magnesium_per_100g": _mg_from_off(nutriments.get("magnesium_100g")),
        "zinc_per_100g": _mg_from_off(nutriments.get("zinc_100g")),
        "potassium_per_100g": _mg_from_off(nutriments.get("potassium_100g")),
        "sodium_per_100g": _mg_from_off(nutriments.get("sodium_100g")),
        "vitamin_c_per_100g": _mg_from_off(nutriments.get("vitamin-c_100g")),
        "vitamin_d_per_100g": _ug_from_off(nutriments.get("vitamin-d_100g")),
        "vitamin_b12_per_100g": _ug_from_off(nutriments.get("vitamin-b12_100g")),
        "vitamin_a_per_100g": _ug_from_off(nutriments.get("vitamin-a_100g")),
        "serving_size": str(product.get("serving_size") or "").strip() or None,
        "serving_quantity_g": _to_float(product.get("serving_quantity")),
        # Tags categories OFF bruts (mappes cote client vers un groupe curate). Liste vide si
        # absent ; on ignore une valeur non-liste eventuelle (robustesse aux formats OFF).
        "categories_tags": [str(c) for c in cats] if isinstance(cats := product.get("categories_tags"), list) else [],
    }


# -------------------- Endpoints --------------------

@nutrition_off_router.get(
    "/nutrition/off/search",
    response_model=list[OffProductOut],
)
async def search_off_products(
    q: str = Query(..., min_length=2, max_length=100),
    page_size: int = Query(20, ge=1, le=50, alias="pageSize"),
    user_id: int = Depends(get_current_user_id),
):
    """Recherche texte OFF, normalisee per-100g. Les produits sans nom/code/
    energie sont filtres (inutilisables comme Food)."""
    cache_key = f"search:{q.strip().lower()}:{page_size}"
    cached = _cache_get(cache_key)
    if cached is not None:
        return cached

    try:
        data = await _fetch_json_retry(
            OFF_SEARCH_URL,
            params={"q": q, "page_size": page_size, "fields": _OFF_FIELDS},
        )
    except HTTPException:
        # Fallback legacy si Search-a-licious est down a son tour.
        data = await _fetch_json(
            f"{OFF_BASE_URL}/cgi/search.pl",
            params={
                "search_terms": q,
                "search_simple": 1,
                "action": "process",
                "json": 1,
                "page_size": page_size,
                "fields": _OFF_FIELDS,
            },
        )
    items = [
        normalized
        for product in (data.get("hits") or data.get("products") or [])
        if (normalized := normalize_off_product(product)) is not None
    ]
    _cache_set(cache_key, items, _SEARCH_TTL_SECONDS)
    return items


@nutrition_off_router.get(
    "/nutrition/off/product/{barcode}",
    response_model=OffProductOut,
)
async def get_off_product(
    barcode: str,
    user_id: int = Depends(get_current_user_id),
):
    """Lookup produit OFF par code-barres, normalise per-100g."""
    if not barcode.isdigit() or len(barcode) > 20:
        raise HTTPException(status_code=400, detail="Code-barres invalide")

    cache_key = f"product:{barcode}"
    cached = _cache_get(cache_key)
    if cached is not None:
        return cached

    data = await _fetch_json_retry(
        f"{OFF_BASE_URL}/api/v2/product/{barcode}.json",
        params={"fields": _OFF_FIELDS},
    )
    product = data.get("product")
    if data.get("status") != 1 or not product:
        raise HTTPException(status_code=404, detail="Produit non trouvé sur Open Food Facts")

    # Le code peut etre au top-level de la reponse v2 plutot que dans product.
    product.setdefault("code", data.get("code", barcode))
    # Scan volontaire d'un code-barres = l'user veut CE produit : on tolere
    # l'absence de champ energie (eau minerale a 0 kcal). Seul un produit sans
    # nom/code reste rejete (entree OFF vide).
    normalized = normalize_off_product(product, allow_missing_energy=True)
    if normalized is None:
        raise HTTPException(
            status_code=404,
            detail="Produit sans nom ni code exploitable",
        )
    _cache_set(cache_key, normalized, _PRODUCT_TTL_SECONDS)
    return normalized
