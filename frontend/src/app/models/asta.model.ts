/** Asta su un annuncio. */

export interface Asta {
  id: number;
  prezzoBase: number;
  offertaAttuale: number;
  scadenza: string;
  annuncioId: number;
  /** Titolo annuncio nelle “aste mie”. */
  annuncioTitolo?: string | null;
  nomeOfferente: string | null;
  /** True se l’ultima offerta vincente è stata ritirata (nessun offerente fino a una nuova offerta). */
  offertaRitirata?: boolean;
  /** Email venditore in lista admin. */
  emailVenditore?: string | null;
  /** Dashboard venditore: c’è un’offerta che non avevi ancora “visto”. */
  nuovaOfferta?: boolean;
}
