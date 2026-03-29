package unical.it.webapp.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import unical.it.webapp.model.StatoAppuntamento;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppuntamentoResponseDTO {

    private Long id;
    private Long annuncioId;
    private String titoloAnnuncio;
    private StatoAppuntamento stato;
    private LocalDateTime createdAt;

    private Long acquirenteId;
    private String nomeAcquirente;
    private String cognomeAcquirente;
    private String emailAcquirente;
}
