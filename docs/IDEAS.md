# IDEAS.md — Boîte à idées (inbox non triée)

> Inbox brute, **zéro contrainte de format**. Balance ici les idées au fil de l'eau,
> en une ligne ou un paragraphe, comme tu veux.
>
> **Workflow** : quand tu dis « trie les idées », Claude évalue chaque entrée
> (feature vs fix, doublon, faisabilité, priorité), la route vers
> [`TODO_FEATURES.md`](TODO_FEATURES.md) ou [`TODO_FIXES.md`](TODO_FIXES.md)
> formatée proprement, puis la **retire d'ici**.
>
> → Une `IDEAS.md` vide = tout est trié.

## À trier

- 2026-05-22 — Quand je crée un programme vide, rajouter les lignes placeholder
  Warmup / Training / Post-training dans le jour (je le remarque en créant un
  programme vide notamment).
- 2026-06-25 — RGPD / politique de rétention des données. **Pas prévu pour le
  moment** (app perso → non applicable), mais **important SI l'app s'ouvre au
  public / multi-utilisateurs** : définir des durées de conservation (purger /
  anonymiser les `refresh_tokens` + `mcp_sessions` expirés et le vieil historique
  de connexion) ; faire d'éventuelles **stats** sur une table d'événements dédiée
  (`login_events`, façon `mcp_audit_log`) plutôt qu'en minant les tokens d'auth
  (= détournement de finalité). Droit à l'effacement déjà couvert par `DELETE /me`
  (cascade FK). Détail : mémoire `project_gdpr_retention_if_public`.
