# Visuels du README

| Fichier | État | Contenu |
|---|---|---|
| `hero.png` | ✅ en place | Séance Push Day, radar de volume par zone, hub Santé |
| `domaines.png` | ✅ en place | Navigation par domaines, calendrier, journal nutritionnel |
| `sync-offline.gif` | ⬜ à produire | Couper le réseau, saisir une série, rétablir : la donnée remonte. 10 à 15 s, < 8 Mo |
| `watch.png` | ⬜ à produire | Écran de la montre : pas et fréquence cardiaque |
| `web.png` | ⬜ à produire | Client web connecté, page d'accueil ou statistiques, ~1600 px de large |

Les bandes horizontales sont composées en HTML puis capturées avec Chrome en mode
sans interface, pour garder un rendu homogène (fond `#0d1117`, coins arrondis,
légendes). Conserver le mode sombre sur tous les visuels.

**Le GIF de synchronisation est le plus important** : c'est la seule façon de montrer
la partie la plus difficile du projet. Capture possible avec `adb shell screenrecord`.
