import { HttpInterceptorFn } from '@angular/common/http';
import { getClientId } from './client-id';

/** Ajoute X-Client-Id à chaque requête (miroir de ClientIdProvider Android). */
export const clientIdInterceptor: HttpInterceptorFn = (req, next) =>
  next(req.clone({ setHeaders: { 'X-Client-Id': getClientId() } }));
