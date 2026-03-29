import { HttpErrorResponse } from '@angular/common/http';

/** Estrae message dal JSON d’errore Spring quando c’è. */
export function parseSpringErrorMessage(err: unknown): string | null {
  if (!(err instanceof HttpErrorResponse) || err.error == null) {
    return null;
  }
  const e = err.error;
  if (typeof e === 'object' && 'message' in e) {
    const m = (e as { message?: unknown }).message;
    if (typeof m === 'string' && m.trim().length > 0) {
      return m;
    }
  }
  if (typeof e === 'string' && e.trim().length > 0) {
    return e;
  }
  return null;
}
