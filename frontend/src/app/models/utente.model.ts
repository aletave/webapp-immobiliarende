/** Utente in UI (niente password). */

/** Ruoli noti (il backend può mandare anche stringhe). */
export type RuoloUtente = 'ADMIN' | 'VENDITORE' | 'ACQUIRENTE';

export interface Utente {
  id: number;
  nome: string;
  cognome: string;
  email: string;
  ruolo: RuoloUtente | string;
  /** Bannato (il server ha l’ultima parola). */
  bannato: boolean;
}
