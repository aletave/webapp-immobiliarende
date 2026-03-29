import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import type { Annuncio, AnnuncioRequest } from '../../models/annuncio.model';

/** Base URL del backend (stesso host usato da AuthService). */
const API_BASE_URL = environment.apiUrl;

/** Filtri catalogo annunci (nomi allineati al backend). */
export interface FiltriAnnunci {
  tipo?: string;
  categoria?: string;
  orderBy?: string;
  direction?: string;
}

/** Chiamate HTTP agli annunci; il JWT lo aggiunge l’interceptor dove serve. */
@Injectable({ providedIn: 'root' })
export class AnnuncioService {
  private readonly http = inject(HttpClient);

  /** Annunci pubblicati dall’utente loggato (venditore/admin). */
  getAnnunciMiei(): Observable<Annuncio[]> {
    return this.http.get<Annuncio[]>(`${API_BASE_URL}/api/annunci/miei`);
  }

  /** Lista catalogo con filtri e ordinamento opzionali. */
  getAnnunci(filtri?: FiltriAnnunci): Observable<Annuncio[]> {
    let params = new HttpParams();
    const f = filtri ?? {};
    if (f.tipo?.trim()) {
      params = params.set('tipo', f.tipo.trim());
    }
    if (f.categoria?.trim()) {
      params = params.set('categoria', f.categoria.trim());
    }
    if (f.orderBy?.trim()) {
      params = params.set('orderBy', f.orderBy.trim());
    }
    if (f.direction?.trim()) {
      params = params.set('direction', f.direction.trim());
    }
    return this.http.get<Annuncio[]>(`${API_BASE_URL}/api/annunci`, { params });
  }

  /** Singolo annuncio (foto caricate lato server con il proxy). */
  getAnnuncio(id: number): Observable<Annuncio> {
    return this.http.get<Annuncio>(`${API_BASE_URL}/api/annunci/${id}`);
  }

  /** POST nuovo annuncio (token + ruolo venditore/admin). */
  creaAnnuncio(annuncio: AnnuncioRequest): Observable<Annuncio> {
    return this.http.post<Annuncio>(`${API_BASE_URL}/api/annunci`, annuncio);
  }

  /** PUT annuncio (proprietario o admin). */
  modificaAnnuncio(id: number, annuncio: AnnuncioRequest): Observable<Annuncio> {
    return this.http.put<Annuncio>(`${API_BASE_URL}/api/annunci/${id}`, annuncio);
  }

  /** DELETE annuncio (204). */
  eliminaAnnuncio(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/api/annunci/${id}`);
  }

  /** Ribasso: backend salva prezzo precedente; fine ISO solo per promo a tempo. */
  ribassaPrezzo(id: number, nuovoPrezzo: number, ribassoFine?: string | null): Observable<Annuncio> {
    return this.http.put<Annuncio>(`${API_BASE_URL}/api/annunci/${id}/ribassa`, {
      nuovoPrezzo,
      ribassoFine: ribassoFine ?? null,
    });
  }

  /** Torna al listino e toglie stato ribasso. */
  annullaRibasso(id: number): Observable<Annuncio> {
    return this.http.put<Annuncio>(`${API_BASE_URL}/api/annunci/${id}/ribassa/annulla`, {});
  }
}
