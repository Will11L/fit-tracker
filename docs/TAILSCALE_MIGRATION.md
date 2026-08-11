# Migration Tailscale — API privée sur le tailnet

> Plan de migration : exposer l'API serveur via **Tailscale**, **couper l'accès public** (de façon réversible), garder le public **dormant** pour une ouverture future éventuelle. Validé avec l'utilisateur le 2026-05-21.
>
> Ce doc sert aussi de **handoff** : destiné à être lu par une session Claude lancée **directement sur la Raspberry Pi en SSH**.

## Objectif

Avant migration, l'API était **publique** : `<public-dns>` → Caddy (Let's Encrypt) → uvicorn, port-forward box `443 → Pi`.

Cible :
- **API en accès Tailscale-only** : adresse stable à vie, fin des soucis DDNS / rotation IPv6.
- **Webhook d'auto-déploiement conservé** via Tailscale Funnel → déploiement **instantané** gardé.
- **Public coupé mais ré-activable** : Caddy + domaine + DDNS restent configurés, dormants.

## Pourquoi

- Adresse Tailscale **stable** → fin du feuilleton AAAA périmé / rotation de préfixe Bouygues / DDNS.
- **Zéro exposition publique de l'API** → surface d'attaque minuscule.
- Le **seul** point public restant = le webhook, déjà durci par signature HMAC GitHub.
- Public gardé dormant → ré-ouverture triviale pour un usage public futur.

## Pré-requis (en place)

- Pi sur le tailnet (`tailscale0` actif), ASUS + S21+ aussi.

## Phases

### ✅ Phase 1 — Tailscale sur le Samsung S21+  *(fait 2026-05-21)*
App Tailscale installée, connectée au tailnet, VPN always-on.

### ✅ Phase 2 — API exposée sur le tailnet en HTTPS  *(fait — commit `9e3c454`)*
`tailscale serve --bg http://localhost:8000` → `https://<pi-fqdn>` (cert géré par Tailscale). `start_api.sh` : `--proxy-headers` ajouté.

### ✅ Phase 3 — App Android pointe sur le tailnet  *(fait — commit `9e3c454` + APK)*
`build.gradle.kts` release → `<pi-fqdn>`. APK release validé runtime S21+ : signup + delete OK en **Wi-Fi et 4G**.

### ✅ Phase 4 — Webhook auto-deploy via Tailscale Funnel  *(fait 2026-05-21)*
`tailscale funnel --bg --https=8443 http://localhost:8001` → `https://<pi-fqdn>:8443/webhook/deploy`. URL GitHub webhook mise à jour. Redeliver GitHub → 200, `deploy.sh` lancé, service redémarré.

### ✅ Phase 5 — Couper le public  *(fait 2026-05-21)*
Livebox NAT/PAT : règles port 80 et 443 → raspberrypi désactivées. Caddy / domaine DDNS / DDNS restent dormants.

### ✅ Phase 6 — Documentation finale  *(fait 2026-05-21)*
`DEV_GUIDE.md` §10 mis à jour : architecture complète serve+funnel, S21+ marqué connecté, commandes funnel, procédure ré-ouverture public (NAT/PAT ports 80+443).

---

## Phase 4 en détail — Webhook via Funnel

**Principe** : `tailscale serve` (privé tailnet) et `tailscale funnel` (public) coexistent sur des **ports différents**.

```
443  → tailscale serve  → uvicorn :8000     (API     — privée, tailnet)
8443 → tailscale funnel → webhook.py :8001  (webhook — public, HMAC-only)
```

L'API reste privée ; seul le webhook devient joignable publiquement — et `webhook.py` vérifie déjà la signature HMAC GitHub (`X-Hub-Signature-256`), donc toute requête non signée est rejetée.

**Checklist (à exécuter sur la Pi) :**

1. **Activer Funnel** dans la console admin Tailscale (capability `funnel` sur le nœud). La commande `tailscale funnel` affiche le lien d'activation si ce n'est pas encore fait.
2. **Funnel le webhook** sur un port séparé, sans toucher au `serve` de l'API :
   `tailscale funnel --bg --https=8443 http://localhost:8001`
   *(confirmer la syntaxe exacte selon la version installée via `tailscale funnel --help` — l'API CLI a un peu bougé selon les versions).*
3. **Vérifier** : `tailscale serve status` → doit montrer 443 = API (tailnet) **et** 8443 = webhook (Funnel / public).
4. **Changer l'URL du webhook GitHub** (repo → Settings → Webhooks) :
   `https://<public-dns>/webhook/deploy` → `https://<pi-fqdn>:8443/webhook/deploy`
   **Secret HMAC inchangé.**
5. **Tester** : "Redeliver" (ou ping) depuis GitHub → doit toucher la Pi, HMAC vérifié, réponse 200.
6. **Seulement une fois l'étape 5 confirmée OK** → passer à la phase 5 (fermer la box).

`webhook.py` ne change pas (il écoute déjà `127.0.0.1:8001`, Funnel tape dessus). Le bloc `/webhook/deploy` de Caddy devient mort → cleanup optionnel, pas urgent.

> ⚠️ **Ordre impératif** : faire Funnel + test (étapes 1-5) **tant que le public est encore ouvert** comme filet de sécurité. Ne fermer la box (phase 5) qu'après confirmation que le webhook Funnel répond. Sinon : risque de se retrouver sans aucun chemin de déploiement.

---

## ⚠️ Point d'attention — certificat Let's Encrypt (public dormant)

Port public fermé → Caddy ne peut plus renouveler son cert public (le challenge HTTP-01 a besoin du `443` entrant depuis Internet). Pendant la dormance, le cert public finit par expirer.
- Ré-ouverture : Caddy renouvelle au redémarrage (court créneau sans cert valide).
- Mieux : basculer Caddy en challenge **DNS-01** → renouvelle sans port entrant.

(Sans objet pour l'API et le webhook : leurs certificats sont gérés automatiquement par Tailscale.)

## État au 2026-05-21 — Migration complète ✅

- Phases 1-6 toutes terminées.
- API : tailnet-only via `tailscale serve` (port 443, cert Tailscale).
- Webhook deploy : public via `tailscale funnel` (port 8443, HMAC GitHub).
- Public (Caddy + DDNS) : dormant, ré-activable en 2 clics NAT/PAT Livebox.
