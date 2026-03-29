package unical.it.webapp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Creazione asta: base offerte, quando scade e su quale annuncio (solo il venditore di quell'annuncio). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AstaRequestDTO {

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true, message = "Il prezzo base deve essere almeno 0,01 €")
    private Double prezzoBase;

    @NotNull
    private LocalDateTime scadenza;

    @NotNull
    private Long annuncioId;
}
