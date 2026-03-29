import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import type { Foto } from '../../models/foto.model';

const API_BASE_URL = 'http://localhost:8080';

/** Foto annuncio: lista pubblica, CRUD con ruoli. */
@Injectable({ providedIn: 'root' })
export class FotoService {
  private readonly http = inject(HttpClient);

  /** Tutte le foto di un annuncio. */
  getFotoAnnuncio(annuncioId: number): Observable<Foto[]> {
    return this.http.get<Foto[]>(`${API_BASE_URL}/api/foto/annuncio/${annuncioId}`);
  }

  /** POST foto con URL (venditore titolare). */
  aggiungiFoto(url: string, annuncioId: number): Observable<Foto> {
    return this.http.post<Foto>(`${API_BASE_URL}/api/foto`, { url, annuncioId });
  }

  /** Upload multipart → byte nel DB. */
  uploadFoto(annuncioId: number, file: File): Observable<Foto> {
    const fd = new FormData();
    // annuncioId prima del file: alcuni server legano meglio i parametri multipart in quest’ordine.
    fd.append('annuncioId', String(annuncioId));
    fd.append('file', file);
    return this.http.post<Foto>(`${API_BASE_URL}/api/foto/upload`, fd);
  }

  /** DELETE foto (204). */
  eliminaFoto(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/api/foto/${id}`);
  }
}
