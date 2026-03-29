package unical.it.webapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Foto in risposta API: id, url e annuncio, senza esporre l'entità JPA. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FotoResponseDTO {

    private Long id;

    private String url;

    private Long annuncioId;
}
