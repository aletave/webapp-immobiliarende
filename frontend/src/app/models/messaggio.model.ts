/** Messaggio di contatto (shape simile al backend). */
export interface Messaggio {
  id?: number;
  nomeMittente: string;
  emailMittente: string;
  testo: string;
  /** Spesso valorizzato lato venditore. */
  annuncioId?: number;
  createdAt?: string;
}
