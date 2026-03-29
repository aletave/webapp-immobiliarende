import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import type { Recensione } from '../../models/recensione.model';

const API_BASE_URL = environment.apiUrl;

/** API recensioni (pubbliche + CRUD acquirente). */
@Injectable({ providedIn: 'root' })
export class RecensioneService {
  private readonly http = inject(HttpClient);

  /** Recensioni di un annuncio (pubblico). */
  getRecensioniAnnuncio(annuncioId: number): Observable<Recensione[]> {
    return this.http.get<Recensione[]>(`${API_BASE_URL}/api/recensioni/annuncio/${annuncioId}`);
  }

  /** POST recensione (acquirente dal token). */
  creaRecensione(testo: string, voto: number, annuncioId: number): Observable<Recensione> {
    return this.http.post<Recensione>(`${API_BASE_URL}/api/recensioni`, {
      testo,
      voto,
      annuncioId,
    });
  }

  /** Le tue recensioni. */
  getMieRecensioni(): Observable<Recensione[]> {
    return this.http.get<Recensione[]>(`${API_BASE_URL}/api/recensioni/mie`);
  }

  /** Recensioni sui tuoi annunci. */
  getRecensioniVenditore(): Observable<Recensione[]> {
    return this.http.get<Recensione[]>(`${API_BASE_URL}/api/recensioni/venditore`);
  }

  /** PUT recensione (solo autore). */
  aggiornaRecensione(id: number, testo: string, voto: number): Observable<Recensione> {
    return this.http.put<Recensione>(`${API_BASE_URL}/api/recensioni/${id}`, {
      testo,
      voto,
    });
  }

  /** DELETE (solo autore). */
  eliminaRecensione(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/api/recensioni/${id}`);
  }
}
