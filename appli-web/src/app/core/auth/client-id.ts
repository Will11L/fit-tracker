import { uuidv4 } from '@core/utils/uuid';

const KEY = 'client_id';

/** Identifiant device stable (persisté) — envoyé en header X-Client-Id (dedup WS côté serveur). */
export function getClientId(): string {
  let id = localStorage.getItem(KEY);
  if (!id) {
    id = uuidv4();
    localStorage.setItem(KEY, id);
  }
  return id;
}
