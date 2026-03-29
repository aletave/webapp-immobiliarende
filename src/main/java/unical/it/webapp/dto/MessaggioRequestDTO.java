package unical.it.webapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Invio messaggio: testo e annuncio. Mittente reale viene dal token (acquirente loggato). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessaggioRequestDTO {

    private String testo;

    private Long annuncioId;
}
