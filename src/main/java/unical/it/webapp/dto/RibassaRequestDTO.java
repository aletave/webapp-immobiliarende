package unical.it.webapp.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Abbassa il prezzo: nuovo importo più basso del listino.
 * Se indichi anche una data di fine, è un ribasso temporaneo e poi torna il prezzo salvato prima.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RibassaRequestDTO {

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true, message = "Il nuovo prezzo deve essere almeno 0,01 €")
    @DecimalMax(value = "999999999.99", inclusive = true, message = "Il nuovo prezzo supera il massimo consentito")
    private Double nuovoPrezzo;

    /** Opzionale: quando scade, il prezzo torna a quello messo da parte in prezzo_precedente. */
    private LocalDateTime ribassoFine;
}
