package unical.it.webapp.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Messaggio in JSON: niente entity Hibernate così non esplodono i lazy load in serializzazione. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessaggioResponseDTO {

    private Long id;

    private String nomeMittente;

    private String emailMittente;

    private String testo;

    private Long annuncioId;

    /** Titolo annuncio quando listi i messaggi del mittente. */
    private String annuncioTitolo;

    private LocalDateTime createdAt;

    /** Se il venditore ha già letto o segnato come visti i messaggi di quell'annuncio. */
    private Boolean lettoVenditore;
}
