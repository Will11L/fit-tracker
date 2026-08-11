# NUTRITION_DESIGN.md — Module Nutrition (calories + macros)

> Document de cadrage du module Nutrition. Construit en session le 2026-06-12 avec l'utilisateur.
> Tâche Notion source : « Module Nutrition (calories + macros) — nouvelles entités Food/Meal/MealEntry/NutritionGoal » (XL, Cross-stack).
> **Statut : VALIDÉ 2026-06-12** — toutes les décisions de cadrage sont actées (D1-D11). Prêt pour le découpage en sous-tâches Notion (V0) puis l'implémentation.

## 1. Vision produit

Un module alimentation type MyFitnessPal mais personnalisé et **très modulable** :

- **Journal quotidien par repas libres** : l'utilisateur définit ses propres périodes/repas (petit-déj, déjeuner, collation pré-training…), pas de slots figés.
- **Vue à plat par aliments** en parallèle : liste filtrable de tout ce qui a été consommé (filtres par macro, par période, par aliment).
- **Repas enregistrés / recettes** : des plats composés et des repas tout faits ajoutables en un tap dans une période de la journée.
- **Objectifs manuels** : cibles quotidiennes kcal + protéines/glucides/lipides, avec suivi jour ET vue agrégée semaine.
- **Catalogue hybride** : aliments génériques français (seed CIQUAL) + produits du commerce (recherche Open Food Facts dès v1) + aliments perso.
- **Stats/historique** : courbes kcal/macros par jour/semaine, dans l'esprit de la page Stats sport.

**Ordre de livraison** : serveur → **web Angular d'abord** (UI testée sur `appli-web/`) → Android plus tard.

## 2. Décisions actées (2026-06-12)

| # | Décision | Choix |
|---|---|---|
| D1 | Structure du journal | Repas libres customisables + vue à plat filtrable (pas de slots figés) |
| D2 | Catalogue v1 | Open Food Facts **dès v1** (la tâche Notion disait v2 — révisé) + seed CIQUAL pour les génériques + aliments perso |
| D3 | Objectifs | Cibles manuelles quotidiennes (kcal + P/G/L). Variantes par type de jour = extension future (schéma prêt, pas d'UI v1). Vue hebdo = agrégation des jours. |
| D4 | Écrans v1 web | Journal + recherche/ajout + objectifs + **stats/historique** |
| D5 | Snapshot macros | `MealEntry` **fige les macros** au moment de l'ajout — l'historique est immuable, corriger un aliment du catalogue ne réécrit pas le passé |
| D6 | Unités | Grammes par défaut + **portions nommées** par aliment (« 1 œuf = 60 g ») ; ml traité comme g (densité ignorée v1) |
| D7 | Recettes | **Entité Recipe complète dès v1**, qui couvre aussi les « repas enregistrés » (cf. §3.3) |
| D8 | Goal hebdo | Pas d'entité dédiée : la vue semaine compare `Σ jours` à `cible×7` |
| D9 | Scope du catalogue | **Tout user-scoped** : CIQUAL copié au signup comme les muscles/exercises (pattern starter_template). ~3 200 rows × user = négligeable à l'échelle de l'app ; un seul pattern de sync, une seule source de recherche, aliments éditables par user. Renoncement accepté : une MAJ future de CIQUAL ne se propage pas aux users existants. |
| D10 | Presets de repas | **Entité `meal_presets`** : périodes habituelles (nom + ordre + heure indicative) gérées par l'utilisateur ; le journal pré-affiche les périodes chaque jour |
| D11 | Micro-nutriments | Fibres / sucres / AG saturés / sel stockés en nullable (dispo CIQUAL + OFF) ; affichage détail seulement, pas de cibles dessus en v1 |
| D12 | Cohérence kcal ↔ macros (2026-06-13) | **Convention unique** : glucides = glucides *disponibles* (fibres exclues), fibres = poste calorique à part. Formule de référence **`kcal = 4·prot + 4·gluc + 9·lip + 2·fibres`** (facteurs Atwater + fibres 2 kcal/g, EU 1169/2011). **Aliments** : kcal *dérivée* des macros pour les bruts (`source=CIQUAL`) ; kcal *de la source* pour les produits étiquetés (`source=OFF`, scan) ; pour `CUSTOM`, kcal saisie si fournie, sinon dérivée. La valeur source reste visible « pour info » dans le détail aliment. **Objectifs** : page *macro-first* — l'utilisateur saisit P/G/L (g), le total kcal se dérive en intégrant l'espace fibres : `total = base / 0,97` avec `base = 4P+4G+9L` (résout la circularité fibres↔kcal puisque fibres = 15 g/1000 kcal du total), puis `fibres_g = 15·total/1000` affichées. Ex. P180/G250/L80 → base 2440 → total 2515 kcal, 38 g fibres. **Conséquence assumée** : un jour mêlant aliments OFF (kcal étiquette) et bruts, le total kcal n'égale pas exactement la somme Atwater (écart ~1-2 %, l'étiquette fait foi). |

## 3. Modèle de données

8 nouvelles tables (Postgres + Room/Dexie symétriques). Toutes suivent les conventions existantes : UUID client-generated, `user_id` jamais lu du payload (politique 8), snake_case DB / camelCase wire (politique 17), états UPPER_CASE (politique 11), `updated_at` pour le last-write-wins, triggers WS NOTIFY (politique 6), DBML synchrone (politique 14).

### 3.1 `foods` — catalogue d'aliments

| Colonne | Type | Notes |
|---|---|---|
| id, uuid, user_id | — | squelette standard Type A |
| name | text NOT NULL | nom affiché |
| brand | text NULL | marque (produits OFF) |
| source | text NOT NULL | `CUSTOM` / `CIQUAL` / `OFF` (UPPER_CASE, politique 11) |
| source_ref | text NULL | code CIQUAL ou barcode OFF (traçabilité + dédup à l'import) |
| kcal_per_100g | float NOT NULL | énergie |
| protein_per_100g | float NOT NULL | protéines g |
| carbs_per_100g | float NOT NULL | glucides g |
| fat_per_100g | float NOT NULL | lipides g |
| fiber_per_100g | float NULL | fibres (optionnel, dispo CIQUAL/OFF) |
| sugar_per_100g | float NULL | sucres (optionnel) |
| sat_fat_per_100g | float NULL | acides gras saturés (optionnel) |
| salt_per_100g | float NULL | sel (optionnel) |
| is_favorite | bool default false | accès rapide dans la recherche |
| archived | bool default false | masquer sans supprimer (l'historique snapshotté survit de toute façon) |

Scope : **user-scoped Type A** (D9). Le seed CIQUAL est copié au signup via le mécanisme starter_template existant (comme muscles/exercises).

### 3.2 `food_portions` — portions nommées

| Colonne | Type | Notes |
|---|---|---|
| id, uuid | — | squelette standard |
| food_uuid | FK foods | cascade ownership via Food → User |
| label | text NOT NULL | « 1 œuf », « 1 portion », « 1 cuillère à soupe » |
| grams | float NOT NULL | équivalent en grammes |

Les `serving_size` d'OFF et les portions CIQUAL alimentent cette table à l'import quand disponibles.

### 3.3 `recipes` + `recipe_ingredients` — plats composés ET repas enregistrés

Une seule entité couvre les deux besoins exprimés :
- **Plat composé** (« mon bol d'avoine ») : ingrédients + quantités, ajouté comme un aliment avec une quantité consommée.
- **Repas enregistré** (« mon petit-déj habituel ») : même structure — l'ajouter à un repas insère ses ingrédients (en un tap).

| `recipes` | Type | Notes |
|---|---|---|
| id, uuid, user_id | — | Type A user-scoped |
| name | text NOT NULL | |
| kind | text NOT NULL | `RECIPE` (plat, macros au prorata du poids consommé) / `SAVED_MEAL` (insertion des ingrédients tels quels) |
| total_weight_g | float NULL | poids final cuit (kind=RECIPE seulement — gère le ratio cru/cuit) |

| `recipe_ingredients` | Type | Notes |
|---|---|---|
| id, uuid | — | |
| recipe_uuid | FK recipes | cascade ownership |
| food_uuid | FK foods | référence vivante (PAS de snapshot ici : une recette est un modèle, pas de l'historique) |
| quantity_g | float NOT NULL | |
| order_index | int NOT NULL | pas de default (politique 10 : valeur positionnelle explicite) |

### 3.4 `meals` — repas du journal

| Colonne | Type | Notes |
|---|---|---|
| id, uuid, user_id | — | Type A |
| date | date NOT NULL | jour du journal |
| name | text NOT NULL | libre, user-typed (« Petit-déj », « Pré-training »…) — jamais traduit |
| order_index | int NOT NULL | ordre d'affichage dans la journée |

Un `Meal` (row en DB) n'est créé que lorsqu'une première entry y est ajoutée — le journal affiche les périodes des `meal_presets` (§3.5) comme sections vides tant qu'aucune entry n'existe, sans créer de rows fantômes.

### 3.5 `meal_presets` — périodes habituelles (D10)

| Colonne | Type | Notes |
|---|---|---|
| id, uuid, user_id | — | Type A |
| name | text NOT NULL | « Petit-déj », « Déjeuner », « Pré-training »… user-typed |
| order_index | int NOT NULL | ordre des sections du journal |
| default_time | time NULL | heure indicative (affichage/tri seulement) |

Seed au signup : 4 presets par défaut (Petit-déj / Déjeuner / Dîner / Collation), renommables/supprimables. Créer un repas hors preset reste possible (repas ad hoc).

### 3.6 `meal_entries` — la table centrale (snapshot)

| Colonne | Type | Notes |
|---|---|---|
| id, uuid | — | |
| meal_uuid | FK meals | cascade ownership Meal → User |
| food_uuid | FK foods, NULL **SET NULL** | référence informative ; l'entry survit à la suppression de l'aliment grâce au snapshot |
| recipe_uuid | FK recipes, NULL SET NULL | si l'entry vient d'une recette kind=RECIPE |
| display_name | text NOT NULL | snapshot du nom (l'entry reste lisible même si Food supprimé/renommé) |
| quantity_g | float NOT NULL | quantité consommée en g |
| portion_label | text NULL | snapshot du label de portion utilisé (« 2 œufs ») pour l'affichage |
| kcal_per_100g, protein_per_100g, carbs_per_100g, fat_per_100g | float NOT NULL | **snapshot D5** — les totaux se dérivent : `total = per_100g × quantity_g / 100` |
| fiber/sugar/sat_fat/salt_per_100g | float NULL | snapshot optionnel |

Snapshot per-100g + quantité (plutôt que totaux) : modifier la quantité d'une entry existante ne demande pas de re-résoudre l'aliment.

### 3.7 `nutrition_goals` — cibles quotidiennes

| Colonne | Type | Notes |
|---|---|---|
| id, uuid, user_id | — | Type A |
| effective_from | date NOT NULL | la cible active un jour J = celle avec le plus grand `effective_from ≤ J` → les stats passées comparent chaque jour à la cible **qui était active ce jour-là** |
| day_kind | text NOT NULL default `ALL` | `ALL` v1 ; extension future par type de jour (D3) sans migration |
| kcal | float NOT NULL | |
| protein_g, carbs_g, fat_g | float NOT NULL | |

### 3.8 Diagramme

```
User 1──n Food 1──n FoodPortion
User 1──n Recipe 1──n RecipeIngredient n──1 Food
User 1──n MealPreset
User 1──n Meal 1──n MealEntry (snapshot ; FK Food/Recipe SET NULL)
User 1──n NutritionGoal
```

## 4. Intégrations externes

### 4.1 Open Food Facts (v1)

- **Architecture : proxy serveur** — endpoints `GET /api/v1/nutrition/off/search?q=...` et `GET /api/v1/nutrition/off/product/{barcode}` côté FastAPI, qui appellent l'API OFF, normalisent vers le format `Food` (per-100g) et mettent en cache (TTL). Avantages : User-Agent conforme aux CGU OFF posé une fois, rate-limiting centralisé, clients (web + Android futur) qui ne connaissent qu'un seul format, pas de CORS.
- **Flux d'import** : recherche OFF → l'utilisateur sélectionne un produit → le client **copie** le produit dans son catalogue `foods` (source=`OFF`, source_ref=barcode) → utilisable offline ensuite. Dédup par `source_ref` (re-sélectionner le même produit réutilise le Food existant).
- Champs récupérés : nutriments per-100g, nom, marque, serving_size → `food_portions`.

### 4.2 CIQUAL (seed des génériques)

- Table ANSES (~3 200 aliments génériques FR, macros validées). Fichier téléchargeable (XLS/CSV), pas d'API.
- Script one-shot `serveur/scripts/import_ciqual.py` : parse le fichier → alimente le catalogue (modalité selon Q1).
- Remplace le « starter pack » d'aliments inventés à la main.

## 5. UI v1 (web Angular `appli-web/`)

Pages (routing + composants, style master-detail cohérent avec l'existant) :

1. **Journal** (`/nutrition`) — page principale. Navigation par jour (← date →), repas dans l'ordre avec leurs entries, sous-totaux par repas, bandeau cumuls jour vs cibles (kcal + 3 macros, barres de progression style app). Actions : ajouter un repas, ajouter un aliment/recette à un repas, dupliquer un repas passé, éditer/supprimer une entry.
2. **Vue à plat** (onglet ou toggle dans le Journal) — liste filtrable des MealEntries (période, repas, tri/filtre par macro, recherche par nom).
3. **Recherche/ajout d'aliment** (drawer ou page `/nutrition/foods`) — onglets : Mon catalogue (favoris/récents en tête) / OFF / Créer un aliment. Détail aliment : macros, portions, édition si CUSTOM.
4. **Recettes & repas enregistrés** (`/nutrition/recipes`) — liste + éditeur (ingrédients, quantités, kind, poids cuit).
5. **Objectifs** (`/nutrition/goals`) — édition des cibles actives + historique.
6. **Stats** (`/nutrition/stats`) — courbes kcal/macros par jour, agrégation semaine vs cible×7, à la manière de la page Stats sport (echarts).

Offline-first : tables Dexie symétriques + branchement dans la sync layer web existante + WS realtime (les triggers NOTIFY couvrent les nouvelles tables).

## 6. Questions tranchées (2026-06-12)

- **Q1 — Scope du catalogue `foods`** : ✅ tout user-scoped, CIQUAL copié au signup (→ D9). L'option « catalogue global Type C » a été écartée : à l'échelle de l'app (poignée d'users), 3 200 rows × user est négligeable, et le pattern unique de sync + recherche mono-source l'emporte.
- **Q2 — Presets de repas** : ✅ entité `meal_presets` dédiée (→ D10, §3.5).
- **Q3 — Micro-nutriments** : ✅ les 4 optionnels nullable (→ D11).
- **Q4 — Compléments alimentaires** : tâche Notion séparée existante — reste **hors scope v1**, le schéma `foods` (source CUSTOM) peut déjà les représenter en attendant.

## 7. Roadmap par vagues

| Vague | Contenu | Stack |
|---|---|---|
| **V0** | Ce doc + validation + découpage Notion (po-sport) | doc |
| **V1** | Serveur : 8 modèles + CRUDs/routers/schemas canoniques + Alembic + triggers WS + DBML + pytest | serveur |
| **V2** | Seed CIQUAL (script import → starter_template) + presets par défaut au signup + proxy OFF + tests | serveur |
| **V3** | Web : tables Dexie + sync layer + services | web |
| **V4** | Web UI : Journal + recherche/ajout (cœur utilisable) | web |
| **V5** | Web UI : recettes/repas enregistrés + objectifs | web |
| **V6** | Web UI : vue à plat filtrable + stats | web |
| **V7+** | Android (entités Room + DAOs + Syncables + écrans Compose + i18n politique 18) — **plus tard**, après validation de l'UX sur le web | android |

Chaque vague serveur respecte les politiques 6/8/9/10/11/14/15/16/17 du CLAUDE.md.
