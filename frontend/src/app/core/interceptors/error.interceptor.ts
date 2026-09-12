import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { SKIP_GLOBAL_ERROR_SNACK } from '../http/http-context.tokens';
import { NotificationService } from '../services/notification.service';
import { formatApiErrorMessage } from '../../shared/utils/api-error.util';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notifications = inject(NotificationService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
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
