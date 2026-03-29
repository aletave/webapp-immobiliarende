package unical.it.webapp.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dati che il client manda per creare o aggiornare un annuncio.
 * Coordinate e venditore li imposta il backend a partire da indirizzo e sessione.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnnuncioRequestDTO {

    @NotBlank
    @Size(max = 200)
    private String titolo;

    @NotBlank
    @Size(max = 20000)
    private String descrizione;

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true, message = "Il prezzo deve essere almeno 0,01 €")
    @DecimalMax(value = "999999999.99", inclusive = true, message = "Il prezzo supera il massimo consentito")
    private Double prezzo;

    @NotNull
    @Min(value = 1, message = "I mq devono essere almeno 1")
    @Max(value = 10_000_000, message = "I mq superano il massimo consentito")
    private Integer mq;

    @NotBlank
    @Pattern(
            regexp = "^(APPARTAMENTO|VILLA|BOX|TERRENO)$",
            message = "Categoria non valida")
    private String categoria;

    @NotBlank
    @Pattern(regexp = "^(VENDITA|AFFITTO)$", message = "Tipo non valido")
    private String tipo;

    @NotBlank
    @Size(max = 500)
    // Solo testo: il server lo traduce in lat/lng (il client non manda le coordinate).
    private String indirizzo;
}
