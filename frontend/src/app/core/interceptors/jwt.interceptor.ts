import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';

/** Bearer su tutte le richieste; 401 (fuori /api/auth/) → logout e /login. */
export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.getToken();

  const handleError = (err: HttpErrorResponse) => {
    if (err.status === 401 && !req.url.includes('/api/auth/')) {
      auth.logout();
      router.navigate(['/login']);
    }
    return throwError(() => err);
  };

  if (!token) {
    return next(req).pipe(catchError(handleError));
  }

  const authReq = req.clone({
    setHeaders: {
      Authorization: `Bearer ${token}`,
    },
  });

  return next(authReq).pipe(catchError(handleError));
};
