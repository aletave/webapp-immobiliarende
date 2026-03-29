package unical.it.webapp.model;

/**
 * Stato di una richiesta di visita immobile: in attesa, completata dopo l’incontro, o rifiutata dal venditore/admin.
 */
public enum StatoAppuntamento {
    PENDING,
    COMPLETATO,
    RIFIUTATO
}
