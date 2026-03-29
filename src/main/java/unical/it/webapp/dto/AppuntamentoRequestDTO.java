package unical.it.webapp.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Nuova richiesta visita: basta l'id annuncio; chi chiede e lo stato li mette il backend. */
@Data
public class AppuntamentoRequestDTO {

    @NotNull
    private Long annuncioId;
}
