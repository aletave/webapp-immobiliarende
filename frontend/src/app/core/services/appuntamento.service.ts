import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of, throwError } from 'rxjs';

import type { Appuntamento } from '../../models/appuntamento.model';

const API_BASE_URL = 'http://localhost:8080';

@Injectable({ providedIn: 'root' })
export class AppuntamentoService {
  private readonly http = inject(HttpClient);

  /** Ultima richiesta visita per questo annuncio; 404 → null. */
  getMioPerAnnuncio(annuncioId: number): Observable<Appuntamento | null> {
    return this.http
      .get<Appuntamento>(`${API_BASE_URL}/api/appuntamenti/mio-per-annuncio/${annuncioId}`)
      .pipe(
        catchError((err: unknown) => {
          if (err instanceof HttpErrorResponse && err.status === 404) {
            return of(null);
          }
          return throwError(() => err);
        })
      );
  }

  /** Crea richiesta in stato PENDING. */
  richiediVisita(annuncioId: number): Observable<Appuntamento> {
    return this.http.post<Appuntamento>(`${API_BASE_URL}/api/appuntamenti`, { annuncioId });
  }

  /** Cronologia di tutte le richieste di visita dell’acquirente autenticato. */
  getMiei(): Observable<Appuntamento[]> {
    return this.http.get<Appuntamento[]>(`${API_BASE_URL}/api/appuntamenti/miei`);
  }

  /** Elenco per il venditore autenticato. */
  getPerVenditore(): Observable<Appuntamento[]> {
    return this.http.get<Appuntamento[]>(`${API_BASE_URL}/api/appuntamenti/venditore`);
  }

  /** Segna visita completata (venditore proprietario o admin). */
  completa(id: number): Observable<Appuntamento> {
    return this.http.put<Appuntamento>(`${API_BASE_URL}/api/appuntamenti/${id}/completa`, {});
  }

  /** Rifiuta richiesta in attesa (venditore proprietario o admin). */
  rifiuta(id: number): Observable<Appuntamento> {
    return this.http.put<Appuntamento>(`${API_BASE_URL}/api/appuntamenti/${id}/rifiuta`, {});
  }
}
