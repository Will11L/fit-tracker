# Visuels du README

| Fichier | État | Contenu |
|---|---|---|
| `hero.png` | ✅ | Bandeau : séance, équilibre par zone, hub Santé |
| `sport.png` | ✅ | Séance en cours, radar par zone, volume par muscle |
| `nutrition.png` | ✅ | Calendrier à anneaux, hydratation et repas, détail par aliment |
| `sante.png` | ✅ | Pas, fréquence cardiaque intraday, sommeil par phase |
| `sync-offline.gif` | ⬜ | Couper le réseau, saisir une série, rétablir : la donnée remonte. 10-15 s, < 8 Mo |
| `watch.png` | ⬜ | Écran de la montre : pas et fréquence cardiaque |
| `web.png` | ⬜ | Client web connecté, ~1600 px de large |

## Méthode

Les bandes sont composées en HTML puis capturées avec Chrome sans interface, ce qui
garantit un rendu homogène : fond `#0d1117` (identique au thème sombre de GitHub),
coins arrondis, ombre portée, légende sous chaque écran.

Les captures d'écran viennent du client Android réel via `adb shell screencap`.
Conserver le mode sombre partout.

**Le GIF de synchronisation reste le visuel manquant le plus important** : c'est la
seule façon de montrer la partie la plus difficile du projet.
