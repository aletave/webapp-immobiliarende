package unical.it.webapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Per il venditore: quanti messaggi non ha ancora aperto, raggruppati per annuncio. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessaggioNonLettiRiepilogoDTO {

    private Long annuncioId;

    private String titoloAnnuncio;

    private long numNonLetti;
}
