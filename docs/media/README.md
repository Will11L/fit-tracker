# Visuels du README

Quatre fichiers à déposer dans ce dossier. Chacun correspond à un bloc commenté
dans le [README](../../README.md) : une fois le fichier en place, il suffit de
**décommenter le bloc** pour que l'image apparaisse.

| Fichier | Contenu attendu | Format |
|---|---|---|
| `hero.png` | Trois écrans Android côte à côte : une séance en cours, les statistiques par muscle, le journal nutrition | PNG, largeur ~1400 px |
| `sync-offline.gif` | Démonstration de la synchronisation hors ligne : couper le réseau, saisir une série, rétablir le réseau, la donnée remonte | GIF, 10 à 15 s, < 8 Mo |
| `web.png` | Le client web, page d'accueil ou statistiques, en pleine largeur | PNG, largeur ~1600 px |
| `watch.png` | L'écran de la montre affichant les pas et la fréquence cardiaque | PNG ou photo |

## Conseils

- **Le GIF de synchronisation est le plus important** : c'est la seule façon de
  montrer la partie la plus difficile du projet. Sans lui, « synchronisation
  hors ligne convergente » reste une affirmation.
- Filmer l'écran du téléphone puis convertir en GIF, ou capturer directement
  avec `adb shell screenrecord`.
- Garder le mode sombre pour tous les visuels, par cohérence.
- Éviter d'y faire figurer des données personnelles réelles.
