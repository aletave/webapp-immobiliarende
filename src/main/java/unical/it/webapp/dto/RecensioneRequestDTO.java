package unical.it.webapp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Nuova recensione: testo, stelle e annuncio; l'autore è chi è loggato come acquirente. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecensioneRequestDTO {

    @NotBlank
    private String testo;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer voto;

    @NotNull
    private Long annuncioId;
}
