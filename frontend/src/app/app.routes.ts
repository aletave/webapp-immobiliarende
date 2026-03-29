import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

/** Route app; pagine lazy-loaded. */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'home' },
  {
    path: 'home',
    loadComponent: () => import('./pages/home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: 'mappa',
    loadComponent: () =>
      import('./pages/mappa-annunci/mappa-annunci.component').then((m) => m.MappaAnnunciComponent),
  },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () => import('./pages/register/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'annunci/:id',
    loadComponent: () =>
      import('./pages/dettaglio-annuncio/dettaglio-annuncio.component').then(
        (m) => m.DettaglioAnnuncioComponent
      ),
  },
  {
    path: 'dashboard',
    children: [
      {
        path: 'acquirente',
        loadComponent: () =>
          import('./pages/dashboard-acquirente/dashboard-acquirente.component').then(
            (m) => m.DashboardAcquirenteComponent
          ),
        canActivate: [authGuard, roleGuard],
        data: { role: 'ACQUIRENTE' },
      },
      {
        path: 'venditore',
        loadComponent: () =>
          import('./pages/dashboard-venditore/dashboard-venditore.component').then(
            (m) => m.DashboardVenditoreComponent
          ),
        canActivate: [authGuard, roleGuard],
        data: { role: 'VENDITORE' },
      },
      {
        path: 'admin',
        loadComponent: () =>
          import('./pages/dashboard-admin/dashboard-admin.component').then(
            (m) => m.DashboardAdminComponent
          ),
        canActivate: [authGuard, roleGuard],
        data: { role: 'ADMIN' },
      },
    ],
  },
];
