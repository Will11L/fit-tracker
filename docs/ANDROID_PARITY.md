# ANDROID_PARITY.md — Rattrapage Android ← web Angular

> **But** : backlog de la dette fonctionnelle d'**Android** (`appli-android/`) vis-à-vis du client **web Angular** (`appli-web/`), à résorber **avant** d'attaquer la feature QR code.
> Établi le **2026-06-17** par comparaison exhaustive des deux surfaces (routes, drawer, modules `feature/`).
> **Source de référence = ce doc** ; **avancement = Kanban Notion sport-app** (Axe/Stack/Priorité).

## Contexte

Depuis la création du client web Angular, le web a pris de l'avance fonctionnelle sur Android. Le **serveur expose déjà tout** (les 8 tables nutrition incluses, puisque le web les consomme) — côté Android c'est donc un **portage cross-stack** (Room + sync + Retrofit + UI), **pas** une nouvelle API à créer.

État **vérifié dans le code** (2026-06-17) :
- Android n'a **aucun module nutrition** (`feature/nutrition` absent ; tables nutrition ni dans Room ni dans la sync).
- Android n'a **aucun écran Matériel dédié** (l'équipement n'est éditable que dans le dialog d'un exercice).

---

## A. Nutrition — domaine entièrement absent d'Android 🥗

Serveur : 8 tables (`food`, `food_portion`, `meal`, `meal_entry`, `meal_preset`, `nutrition_goal`, `recipe`, `recipe_ingredient`). Web : 5 pages + couche Dexie. Android : **0**.

### A1 — Couche données + sync (prérequis bloquant) 🔴
Pour les 8 entités : modèle **Room** (+ migration Room versionnée) · **DAO** squelette Style A (politique 9) · **Syncable** · **Retrofit Api** · enregistrement dans `SyncRegistry`/`SyncEngine` en **ordre FK-aware**. Inclut les champs **micros** (vitamines/minéraux) sur `food`/`meal_entry`. Miroir inverse de la couche Dexie web. **Bloque A2-A6.**

### A2 — Écran Journal du jour
Navigation ← jour → (retour aujourd'hui) · calendrier mensuel (anneaux kcal/macros par jour) · bandeau cumuls vs cibles actives (barres **ou** anneaux, macros + **micros** repliables) · sections repas (`meal_presets` + ad hoc) · ajout/édition/suppression d'entrée · **dupliquer un repas passé** · portions nommées.

### A3 — Catalogue d'aliments
Recherche plein-texte (nom + marque) · **filtres multi-critères** (seuils ≥/≤ macro/micro par 100 g, combinables) · **import OpenFoodFacts** · création/édition/archivage d'aliments custom · portions nommées.

### A4 — Recettes & repas enregistrés
`RECIPE` (snapshot poids cuit, insertion au pro-rata) vs `SAVED_MEAL` (modèle prêt-à-insérer) · liste d'ingrédients (réordonnable) · ajout au journal · agrégats macro/micro.

### A5 — Objectifs nutrition
Cible active (kcal + P/G/L + fibres) · **historique des cibles** par `effectiveFrom` (rétroactif) · donut répartition calorique · radar profil macro · comparaison 7 jours.

### A6 — Stats nutrition
Graphes par macro (Calories/Glucides/Lipides/Protéines/Fibres) · **top aliments** par macro · sélecteur de période (1 sem → Tout + perso) · bascule barres/courbes.
⚠️ **NE PAS reprendre la grille tout-en-un du web** (5 cartes simultanées) : écran trop petit → **garder un sélecteur de macro** (un graphe à la fois). Cf. mémoire `nutrition-android-nav-mode`.

### A7 — Navigation nutrition (décision user 2026-06-17)
- **Section « Nutrition » dans le drawer** (Journal / Objectifs / Stats / Catalogue / Recettes), miroir du drawer web.
- **+ bouton bascule Sport↔Nutrition dans la bottom nav**, avec **code couleur** (Sport bleu / Nutrition dark orange `#9D5300` = `--c-dark-orange` web). Reprend la logique `appli-web/src/app/shell/nav-mode.ts` (mode persisté, suit la page) ; remplace le burger (drawer ouvrable au swipe sur Android).

### A8 — Transverse à A1-A7
**i18n EN/FR** de tous les strings nutrition (politique 18) · `app/diagram.dbml` + `docs/DATABASES.md` synchro (politiques 14/16) · entrée CLAUDE.md + `docs/ARCHITECTURE.md` si l'archi bouge (politique 19).

---

## B. Écran Matériel dédié 🏋️
Le web a une page autonome `/materiel` (`materiel-page.ts`) ; Android ne gère l'équipement **que** dans le dialog d'un exercice. → Créer un `feature/equipment` (liste + détail) : catalogue global (`Equipment`) + équipement perso (`AvailableEquipment`, toggle « mon matériel »), filtre possession, exercices liés. Vérifier la sync `AvailableEquipment` (Room/Syncable) si pas déjà branchée.

## C. Réorganisation du drawer 🧭
Le drawer Android mélange tout dans une section « Activité » (9 items). Cible = découpage thématique du web : **Général** (Accueil, Notifications, Routines, Citations) · **Sport** (Séance, Calendrier & Objectifs, Programme, Stats, Matériel, Exercices, Muscles, Chrono) · **Nutrition** (5 items, cf. A7) · **Compte & Réglages**. Couplé à A7 (la branche Nutrition + le toggle bottom nav).

## D. Parité « polish » (basse priorité) ✨
- Radar (volume par zone) dans Stats Android (le web l'a).
- Home en dashboard 2 colonnes (Séance + Nutrition) façon web — dépend de A.
- Fusion Calendrier + Objectifs sur une page (Android les a déjà, juste séparés).

---

## Parité inverse (web ← Android) — hors scope, pour mémoire
Android a / le web n'a pas : **Conversations** (agent IA in-app), **Delavier Method**, **Demo Tour**, **Export Data**, overlays chrono/timer globaux, étape **Bio** de l'onboarding. À traiter dans un futur rattrapage *web*, backlog séparé.

## Séquencement suggéré
**A1** (couche data/sync) → **B** (Matériel : indépendant, petit, bon « warm-up ») → **A2…A6** (incrémental) → **A7 / C** (nav + drawer, une fois la nutrition navigable) → puis **QR code**.

## Suivi Notion
10 tâches créées dans le Kanban sport-app le 2026-06-17 (toutes en **Backlog**). A1 = Cross-stack, le reste Android.

| Réf | Tâche | Priorité | ID Notion |
|---|---|---|---|
| A1 | Nutrition Android A1 — Couche données + sync (bloquant) | 🔴 Haute | `3821776c-d0e2-817c-b5c8-e9065f21ed53` |
| A2 | Nutrition Android A2 — Écran Journal du jour | 🔴 Haute | `3821776c-d0e2-81a2-a6d7-e5648f7dde4e` |
| A3 | Nutrition Android A3 — Catalogue d'aliments | 🟡 Moyenne | `3821776c-d0e2-81ba-a64c-f76723dc1a33` |
| A4 | Nutrition Android A4 — Recettes & repas enregistrés | 🟡 Moyenne | `3821776c-d0e2-8160-bbbc-fda8e2d9bf19` |
| A5 | Nutrition Android A5 — Objectifs nutrition | 🟡 Moyenne | `3821776c-d0e2-8160-bd7a-f52aeb368ee6` |
| A6 | Nutrition Android A6 — Stats nutrition (sélecteur de macro) | 🟡 Moyenne | `3821776c-d0e2-8158-a49a-dd329e55a6c5` |
| A7 | Nutrition Android A7 — Navigation (drawer + toggle bottom nav) | 🟡 Moyenne | `3821776c-d0e2-81c6-8260-eb5cbff6e84b` |
| B | Android — Écran Matériel dédié | 🟡 Moyenne | `3821776c-d0e2-8150-8542-c60819503d2d` |
| C | Android — Réorganisation du drawer par thèmes | 🟢 Basse | `3821776c-d0e2-816c-9311-ff347acdf0f7` |
| D | Android — Parité polish web | 🟢 Basse | `3821776c-d0e2-81a0-b018-c91b8224b75e` |
