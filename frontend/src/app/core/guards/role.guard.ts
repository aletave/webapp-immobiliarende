import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Abilita la route solo se ruolo utente = data.role. */
export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const expected = route.data['role'] as string | undefined;
  const user = auth.getUser();

  if (!expected || !user) {
    return router.createUrlTree(['/home']);
  }

  if (user.ruolo === expected) {
    return true;
  }

  /* Admin promosso da venditore: stessa dashboard venditore per gestire i propri annunci. */
  if (expected === 'VENDITORE' && user.ruolo === 'ADMIN') {
    return true;
  }

  return router.createUrlTree(['/home']);
};
