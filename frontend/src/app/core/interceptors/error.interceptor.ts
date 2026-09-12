import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ApiErrorResponse } from '../../models/api.model';
import { SKIP_GLOBAL_ERROR_SNACK } from '../http/http-context.tokens';
import { NotificationService } from '../services/notification.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notifications = inject(NotificationService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (!req.context.get(SKIP_GLOBAL_ERROR_SNACK)) {
        const apiError = error.error as ApiErrorResponse | undefined;
        const message =
          apiError?.message ||
          (typeof error.error === 'string' ? error.error : null) ||
          error.message ||
          'Request failed';
        notifications.error(message);
      }
      return throwError(() => error);
    })
  );
};
