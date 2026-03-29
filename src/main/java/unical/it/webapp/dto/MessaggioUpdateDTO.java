package unical.it.webapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Modifica messaggio: si cambia solo il testo; nome mittente resta allineato al profilo. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessaggioUpdateDTO {

    private String testo;
}
