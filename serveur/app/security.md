# 🔐 Security Policy - SportAPI

## ✅ Actuellement en place

1. **Authentification par JWT Token**
   - Récupération via `/token-helper` avec login/mot de passe.
   - Utilisation obligatoire du token pour accéder aux routes protégées.

2. **Accès à `/token-helper`**
   - Accessible publiquement.
   - La sécurité repose sur le bon choix du **nom d'utilisateur et mot de passe**.
   - Si besoin, protection supplémentaire possible (voir "Améliorations futures").

3. **Pas de filtrage IP**
   - Aucune IP n'est bloquée par défaut pour permettre un accès universel.
   - Pas de blocage spécifique par région ou pays.

---

## 🚧 Améliorations futures possibles

1. **Protection par HTTP Basic Auth pour `/token-helper`**
   - Pour éviter que des curieux tombent sur cette page.

2. **Blocage par IP ou géolocalisation**
   - Non mis en place actuellement.
   - À étudier si un abus est détecté.

3. **Remplacer `/token-helper` par une vraie page de connexion dans l'application cliente**
   - Pour une meilleure expérience utilisateur.

4. **Mise en place de HSTS et autres headers de sécurité**
   - Possible via Caddy ou autre reverse proxy.

---

## 🛡️ Rappels

- Changez régulièrement les mots de passe si vous soupçonnez une fuite.
- Ne partagez jamais vos tokens publiquement.
- Les tokens expirent rapidement, il faut les renouveler en se reconnectant.
