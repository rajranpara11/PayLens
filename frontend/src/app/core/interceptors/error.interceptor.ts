import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { SKIP_GLOBAL_ERROR_SNACK } from '../http/http-context.tokens';
import { NotificationService } from '../services/notification.service';
import { formatApiErrorMessage } from '../../shared/utils/api-error.util';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notifications = inject(NotificationService);
  const auth = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      const isAuthEndpoint = req.url.includes('/auth/login') || req.url.includes('/auth/me');

      if (error.status === 401 && !isAuthEndpoint) {
        auth.clearSession();
        if (!router.url.startsWith('/login')) {
          void router.navigate(['/login'], {
            queryParams: { reason: 'session' },
          });
        }
        if (!req.context.get(SKIP_GLOBAL_ERROR_SNACK)) {
          notifications.error('Session expired. Please sign in again.');
        }
        return throwError(() => error);
      }

      if (!req.context.get(SKIP_GLOBAL_ERROR_SNACK)) {
        const message =
          formatApiErrorMessage(error.error, error.message || 'Request failed') ||
          'Request failed';
        notifications.error(message);
      }
      return throwError(() => error);
    })
  );
};
