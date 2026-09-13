import { HttpInterceptorFn } from '@angular/common/http';

/** Always send session cookies to the API (needed for cross-origin demos). */
export const credentialsInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req.clone({ withCredentials: true }));
};
