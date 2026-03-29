import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import type { Utente } from '../../models/utente.model';

/** Base URL del backend Spring Boot (CORS abilitato lato server). */
const API_BASE_URL = 'http://localhost:8080';

/** Chiave localStorage per il JWT (stesso valore usato dall'interceptor). */
export const AUTH_TOKEN_STORAGE_KEY = 'auth_token';

/** Chiave localStorage per il profilo utente serializzato (JSON). */
export const AUTH_USER_STORAGE_KEY = 'auth_user';

/** Risposta login API (stesso shape del DTO Java; l’email arriva dal form). */
interface LoginResponseDTO {
  token: string;
  ruolo: string;
  nome: string;
  cognome: string;
  id: number;
}

/** Login, register, logout e lettura token/utente da localStorage. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  /** POST login, salva token e profilo. */
  login(email: string, password: string): Observable<Utente> {
    return this.http
      .post<LoginResponseDTO>(`${API_BASE_URL}/api/auth/login`, { email, password })
      .pipe(
        tap((dto) => this.persistAfterLogin(dto, email)),
        map((dto) => this.mapLoginToUtente(dto, email))
      );
  }

  /** POST register (solo stato HTTP, niente body). */
  register(
    nome: string,
    cognome: string,
    email: string,
    password: string,
    ruolo: string
  ): Observable<void> {
    return this.http.post<void>(`${API_BASE_URL}/api/auth/register`, {
      nome,
      cognome,
      email,
      password,
      ruolo,
    });
  }

  /** DELETE account con JWT. */
  deleteAccount(): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/api/account`);
  }

  /** Logout: pulisce localStorage. */
  logout(): void {
    localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY);
    localStorage.removeItem(AUTH_USER_STORAGE_KEY);
  }

  /** Token o null. */
  getToken(): string | null {
    return localStorage.getItem(AUTH_TOKEN_STORAGE_KEY);
  }

  /** Profilo da localStorage o null. */
  getCurrentUser(): Utente | null {
    return this.readUserFromStorage();
  }

  /** Alias di getCurrentUser(). */
  getUser(): Utente | null {
    return this.getCurrentUser();
  }

  /** C’è token e snapshot utente. */
  isLoggedIn(): boolean {
    return !!this.getToken() && !!this.getCurrentUser();
  }

  /** Ruolo dal payload JWT (solo lettura, senza verificare firma). */
  getRuoloFromToken(): string | null {
    const token = this.getToken();
    if (!token) {
      return null;
    }
    const payload = this.decodeJwtPayload(token);
    const ruolo = payload?.['ruolo'];
    return typeof ruolo === 'string' ? ruolo : null;
  }

  /** Salva token e snapshot utente dopo risposta login. */
  private persistAfterLogin(dto: LoginResponseDTO, email: string): void {
    const utente = this.mapLoginToUtente(dto, email);
    localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, dto.token);
    localStorage.setItem(AUTH_USER_STORAGE_KEY, JSON.stringify(utente));
  }

  /** Converte DTO login + email del form in Utente (bannato non è nel DTO: default false). */
  private mapLoginToUtente(dto: LoginResponseDTO, email: string): Utente {
    return {
      id: dto.id,
      nome: dto.nome,
      cognome: dto.cognome,
      email,
      ruolo: dto.ruolo,
      bannato: false,
    };
  }

  /** Legge e valida JSON utente da localStorage. */
  private readUserFromStorage(): Utente | null {
    const raw = localStorage.getItem(AUTH_USER_STORAGE_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as Utente;
    } catch {
      return null;
    }
  }

  /** Payload JWT parsato (solo lettura client). */
  private decodeJwtPayload(token: string): Record<string, unknown> | null {
    try {
      const parts = token.split('.');
      if (parts.length < 2) {
        return null;
      }
      const base64Url = parts[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
      const json = atob(padded);
      return JSON.parse(json) as Record<string, unknown>;
    } catch {
      return null;
    }
  }
}
