import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import type { Asta } from '../../models/asta.model';
import type { Recensione } from '../../models/recensione.model';
import type { Utente } from '../../models/utente.model';

const API_BASE_URL = 'http://localhost:8080';

/** Chiamate /api/admin/... */
@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);

  getUtenti(): Observable<Utente[]> {
    return this.http.get<Utente[]>(`${API_BASE_URL}/api/admin/utenti`);
  }

  /** Imposta l’utente come bannato (non può accedere). */
  bannaUtente(id: number): Observable<Utente> {
    return this.http.put<Utente>(`${API_BASE_URL}/api/admin/utenti/${id}/banna`, {});
  }

  /** Rimuove il ban (sbanna): l’utente può di nuovo accedere. */
  sbannaUtente(id: number): Observable<Utente> {
    return this.http.put<Utente>(`${API_BASE_URL}/api/admin/utenti/${id}/sbanna`, {});
  }

  promuoviAdmin(id: number): Observable<Utente> {
    return this.http.put<Utente>(`${API_BASE_URL}/api/admin/utenti/${id}/promuovi`, {});
  }

  eliminaAnnuncio(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/api/admin/annunci/${id}`);
  }

  eliminaRecensione(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/api/admin/recensioni/${id}`);
  }

  getRecensioni(): Observable<Recensione[]> {
    return this.http.get<Recensione[]>(`${API_BASE_URL}/api/admin/recensioni`);
  }

  /** Elenco di tutte le aste (moderazione). */
  getAste(): Observable<Asta[]> {
    return this.http.get<Asta[]>(`${API_BASE_URL}/api/admin/aste`);
  }

  /** Rimuove un’asta; l’annuncio resta pubblicato. */
  eliminaAsta(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/api/admin/aste/${id}`);
  }

  getStatsAnnunciPerCategoria(): Observable<Record<string, number>> {
    return this.http.get<Record<string, number>>(
      `${API_BASE_URL}/api/admin/stats/annunci-per-categoria`
    );
  }

  getStatsAnnunciPerTipo(): Observable<Record<string, number>> {
    return this.http.get<Record<string, number>>(
      `${API_BASE_URL}/api/admin/stats/annunci-per-tipo`
    );
  }
}
