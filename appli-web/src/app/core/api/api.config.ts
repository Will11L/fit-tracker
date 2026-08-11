/**
 * Base REST. Relative : en dev, proxifiée vers le backend via proxy.conf.json
 * (évite CORS) ; en prod, servie same-origin que l'API. Préfixe /api/v1 = politique T3.2.
 */
export const API_BASE_URL = '/api/v1';
