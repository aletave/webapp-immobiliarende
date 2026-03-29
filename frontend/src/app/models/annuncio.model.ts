/** Annuncio in lista o dettaglio. */

import type { Foto } from './foto.model';

/** Body create/update: niente id né venditore, li mette il server. */
export interface AnnuncioRequest {
  titolo: string;
  descrizione: string;
  prezzo: number;
  mq: number;
  categoria: string;
  tipo: string;
  /** Indirizzo testuale; il backend calcola lat/lng con Nominatim. */
  indirizzo: string;
}

export interface Annuncio {
  id: number;
  titolo: string;
  descrizione: string;
  prezzo: number;
  /** Prezzo precedente (es. dopo ribasso) per prezzo barrato in UI. */
  prezzoPrecedente: number | null;
  /** Se valorizzato e nel futuro, il ribasso è temporaneo e scade a questa data/ora (ISO). */
  ribassoFine?: string | null;
  mq: number;
  categoria: string;
  tipo: string;
  lat: number;
  lng: number;
  /** Indirizzo salvato con l’annuncio (per modifica form). */
  indirizzo?: string | null;
  createdAt: string;
  nomeVenditore: string;
  cognomeVenditore: string;
  /** Email venditore per contatto (viene dal backend). */
  emailVenditore?: string | null;
  venditoreId: number;
  foto: Foto[];
  /** True se esiste un'asta e la scadenza è nel futuro (lista/card). */
  astaInCorso?: boolean | null;
  /** ISO date — solo se astaInCorso. */
  astaScadenza?: string | null;
  /** Miglior offerta — solo se astaInCorso. */
  astaOffertaAttuale?: number | null;
}
