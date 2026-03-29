package unical.it.webapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Aggiungi foto: URL pubblico e a quale annuncio attaccarla. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FotoRequestDTO {

    @NotBlank
    private String url;

    @NotNull
    private Long annuncioId;
}
