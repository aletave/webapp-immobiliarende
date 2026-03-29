import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import type { Asta } from '../../models/asta.model';

const API_BASE_URL = 'http://localhost:8080';

/** API aste: lettura, creazione venditore, offerte acquirente. */
@Injectable({ providedIn: 'root' })
export class AstaService {
  private readonly http = inject(HttpClient);

  /** Aste collegate a un annuncio (array vuoto se non ce ne sono). */
  getAsteAnnuncio(annuncioId: number): Observable<Asta[]> {
    return this.http.get<Asta[]>(`${API_BASE_URL}/api/aste/annuncio/${annuncioId}`);
  }

  /** POST asta; scadenza in ISO come vuole il backend. */
  creaAsta(prezzoBase: number, scadenza: string, annuncioId: number): Observable<Asta> {
    return this.http.post<Asta>(`${API_BASE_URL}/api/aste`, {
      prezzoBase,
      scadenza,
      annuncioId,
    });
  }

  /** POST offerta (body con importo e astaId). */
  faiOfferta(astaId: number, importo: number): Observable<Asta> {
    return this.http.post<Asta>(`${API_BASE_URL}/api/aste/${astaId}/offerta`, {
      importo,
      astaId,
    });
  }

  /** Aste dove sei in testa (JWT acquirente). */
  getMieAste(): Observable<Asta[]> {
    return this.http.get<Asta[]>(`${API_BASE_URL}/api/aste/mie`);
  }

  /** Le tue aste sui tuoi annunci. */
  getAsteVenditore(): Observable<Asta[]> {
    return this.http.get<Asta[]>(`${API_BASE_URL}/api/aste/venditore`);
  }

  /** Segna offerte come viste (via badge in dashboard venditore). */
  segnaOfferteVisteVenditore(): Observable<void> {
    return this.http.post<void>(`${API_BASE_URL}/api/aste/venditore/segna-offerte-viste`, {});
  }

  /** Cambia la tua offerta mentre sei offerente. */
  modificaMiaOfferta(astaId: number, importo: number): Observable<Asta> {
    return this.http.put<Asta>(`${API_BASE_URL}/api/aste/${astaId}/mia-offerta`, {
      importo,
      astaId,
    });
  }

  /** Ritiro offerta → base, niente offerente. */
  ritiraOfferta(astaId: number): Observable<Asta> {
    return this.http.delete<Asta>(`${API_BASE_URL}/api/aste/${astaId}/mia-offerta`);
  }
}
