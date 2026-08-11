/** Réponse de POST /api/v1/token et /api/v1/refresh — wire en snake_case (exception au camelCase). */
export interface TokenResponse {
  access_token: string;
  refresh_token: string;
  token_type: string;
}
