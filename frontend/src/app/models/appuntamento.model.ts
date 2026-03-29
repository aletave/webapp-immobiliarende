/** Stesso shape della risposta Java AppuntamentoResponseDTO. */
export type StatoAppuntamento = 'PENDING' | 'COMPLETATO' | 'RIFIUTATO';

export interface Appuntamento {
  id: number;
  annuncioId: number | null;
  titoloAnnuncio: string | null;
  stato: StatoAppuntamento;
  createdAt: string;
  acquirenteId: number | null;
  nomeAcquirente: string | null;
  cognomeAcquirente: string | null;
  emailAcquirente: string | null;
}
