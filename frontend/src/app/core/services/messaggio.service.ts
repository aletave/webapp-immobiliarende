import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of } from 'rxjs';

const API_BASE_URL = 'http://localhost:8080';

/** Body POST messaggio (come nel DTO Java). */
export interface MessaggioInvioDto {
  testo: string;
  annuncioId: number;
}

/** Riga in “i miei messaggi”. */
export interface MessaggioMioDto {
  id: number;
  nomeMittente: string;
  emailMittente: string;
  testo: string;
  annuncioId: number;
  annuncioTitolo?: string | null;
  createdAt: string;
}

/** Per annuncio: quanti messaggi il venditore non ha ancora aperto. */
export interface MessaggioRiepilogoNonLettiDto {
  annuncioId: number;
  titoloAnnuncio: string;
  numNonLetti: number;
}

/** Messaggi di contatto su annuncio (acquirente autenticato). */
@Injectable({ providedIn: 'root' })
export class MessaggioService {
  private readonly http = inject(HttpClient);

  /** POST messaggio su annuncio. */
  inviaMessaggio(dto: MessaggioInvioDto): Observable<any> {
    return this.http.post(`${API_BASE_URL}/api/messaggi`, dto);
  }

  /** Messaggi per annuncio (venditore); anche segna lettura. */
  getMessaggiAnnuncio(annuncioId: number): Observable<any[]> {
    return this.http.get<any[]>(`${API_BASE_URL}/api/messaggi/annuncio/${annuncioId}`);
  }

  /** Riepilogo non letti per dashboard venditore. */
  getRiepilogoNonLettiVenditore(): Observable<MessaggioRiepilogoNonLettiDto[]> {
    return this.http
      .get<MessaggioRiepilogoNonLettiDto[]>(
        `${API_BASE_URL}/api/messaggi/venditore/riepilogo-non-letti`
      )
      .pipe(catchError(() => of([])));
  }

  /** Messaggi inviati dall’acquirente loggato. */
  getMieiMessaggi(): Observable<MessaggioMioDto[]> {
    return this.http.get<MessaggioMioDto[]>(`${API_BASE_URL}/api/messaggi/miei`);
  }

  /** PUT testo (solo mittente). */
  aggiornaMessaggio(id: number, testo: string): Observable<MessaggioMioDto> {
    return this.http.put<MessaggioMioDto>(`${API_BASE_URL}/api/messaggi/${id}`, {
      testo,
    });
  }

  /** DELETE: mittente o venditore dell’annuncio. */
  eliminaMessaggio(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/api/messaggi/${id}`);
  }
}
