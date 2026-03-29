/** Recensione su annuncio. */

export interface Recensione {
  id: number;
  testo: string;
  voto: number;
  createdAt: string;
  nomeAcquirente: string;
  /** Cognome autore se il backend lo manda. */
  cognomeAcquirente?: string | null;
  acquirenteId: number;
  /** Id annuncio (admin / qualche GET). */
  annuncioId?: number;
  /** Titolo annuncio nelle “mie recensioni”. */
  annuncioTitolo?: string | null;
}

/** Stringa display autore recensione. */
export function nomeCompletoAutoreRecensione(r: Recensione): string {
  const n = r.nomeAcquirente?.trim() ?? '';
  const c = r.cognomeAcquirente?.trim() ?? '';
  const s = [n, c].filter(Boolean).join(' ');
  return s.length > 0 ? s : '—';
}
