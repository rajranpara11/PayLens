import { HttpContextToken } from '@angular/common/http';

/** When true, the global error interceptor skips the snackbar (page shows its own error UI). */
export const SKIP_GLOBAL_ERROR_SNACK = new HttpContextToken<boolean>(() => false);
