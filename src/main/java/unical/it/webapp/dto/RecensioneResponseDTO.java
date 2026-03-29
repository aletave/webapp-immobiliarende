package unical.it.webapp.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Recensione serializzabile: testo, voto, data e chi l'ha scritta, senza tirare dentro JPA. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecensioneResponseDTO {

    private Long id;

    private String testo;

    private Integer voto;

    private LocalDateTime createdAt;

    private String nomeAcquirente;

    /** Cognome dell'autore, messo accanto al nome in UI. */
    private String cognomeAcquirente;

    private Long acquirenteId;

    /** Annuncio recensito; comodo nelle schermate admin. */
    private Long annuncioId;

    /** Titolo annuncio quando mostri le recensioni dell'acquirente. */
    private String annuncioTitolo;
}
