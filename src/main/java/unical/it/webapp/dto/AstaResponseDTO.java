package unical.it.webapp.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Asta come JSON: prezzi, scadenza, collegamento all'annuncio e chi offre (se c'è). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AstaResponseDTO {

    private Long id;

    private Double prezzoBase;

    private Double offertaAttuale;

    private LocalDateTime scadenza;

    private Long annuncioId;

    /** Titolo dell'annuncio, comodo nelle “aste mie” dell'acquirente. */
    private String annuncioTitolo;

    private String nomeOfferente;

    /** Vero se l'ultima mossa è stato un ritiro: finché non rioffrono, non c'è offerente “vincente”. */
    private Boolean offertaRitirata;

    /** Email venditore, usata nella lista admin. */
    private String emailVenditore;

    /** Per il venditore: c'è un'offerta che non aveva ancora “visto” dall'ultimo check. */
    private Boolean nuovaOfferta;
}
