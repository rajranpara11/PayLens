import { ApiErrorResponse } from '../../models/api.model';

/** Build a user-facing message from an API error body (includes fieldErrors when present). */
export function formatApiErrorMessage(
  errorBody: unknown,
  fallback = 'Request failed'
): string {
  const apiError = errorBody as ApiErrorResponse | undefined;
  if (!apiError || typeof apiError !== 'object') {
    return typeof errorBody === 'string' && errorBody.trim() ? errorBody : fallback;
  }

  const fieldErrors = apiError.fieldErrors ?? [];
  if (fieldErrors.length > 0) {
    const details = fieldErrors
      .slice(0, 3)
      .map((item) => `${item.field}: ${item.message}`)
      .join('; ');
    if (!apiError.message || apiError.message === 'Validation failed') {
      return details;
    }
    if (apiError.message.includes(':')) {
      return apiError.message;
    }
    return `${apiError.message} (${details})`;
  }

  return apiError.message || fallback;
}

/** Local calendar date as YYYY-MM-DD (not UTC). */
export function localDateIso(date = new Date()): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
