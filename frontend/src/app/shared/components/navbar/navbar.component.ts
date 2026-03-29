import { CommonModule } from '@angular/common';
import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter, Subscription } from 'rxjs';

import { AuthService } from '../../../core/services/auth.service';
import type { Utente } from '../../../models/utente.model';

/** Tipi per Bootstrap Collapse caricato da angular.json. */
interface BootstrapCollapseStatic {
  getOrCreateInstance(element: Element): { hide(): void };
}

interface WindowWithBootstrap extends Window {
  bootstrap?: { Collapse: BootstrapCollapseStatic };
}

/** Navbar: menu per ruolo e logout. */
@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.scss',
})
export class NavbarComponent implements OnInit, OnDestroy {
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);

  /** Router: aggiorna utente a ogni navigazione. */
  private navSub?: Subscription;

  /** Utente in header o null. */
  user: Utente | null = null;

  /** Sincronizza user da AuthService. */
  ngOnInit(): void {
    this.syncUser();
    this.navSub = this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe(() => this.syncUser());
  }

  /** Cleanup subscription router. */
  ngOnDestroy(): void {
    this.navSub?.unsubscribe();
  }

  /** Ricopia getUser() nello stato del componente. */
  syncUser(): void {
    this.user = this.authService.getUser();
  }

  /** Tap su link/button: chiudi menu mobile. */
  onNavbarMainClick(event: MouseEvent): void {
    const t = event.target;
    if (!(t instanceof Element)) {
      return;
    }
    const interactive = t.closest('a, button');
    if (!interactive) {
      return;
    }
    this.closeMobileNav();
  }

  /** Chiudi collapse Bootstrap sotto lg. */
  closeMobileNav(): void {
    if (typeof window === 'undefined' || !window.matchMedia('(max-width: 991.98px)').matches) {
      return;
    }
    const el = document.getElementById('navbarMain');
    if (!el) {
      return;
    }
    const Collapse = (window as unknown as WindowWithBootstrap).bootstrap?.Collapse;
    Collapse?.getOrCreateInstance(el).hide();
  }

  /** Logout e vai al login. */
  logout(): void {
    this.authService.logout();
    void this.router.navigate(['/login']);
  }
}
