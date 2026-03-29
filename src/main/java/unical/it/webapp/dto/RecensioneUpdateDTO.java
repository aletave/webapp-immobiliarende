package unical.it.webapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Modifica recensione: si aggiornano solo testo e voto, l'annuncio resta quello di prima. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecensioneUpdateDTO {

    private String testo;

    private Integer voto;
}
