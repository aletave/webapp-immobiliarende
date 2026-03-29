package unical.it.webapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Dopo il login: JWT per le chiamate protette e un pizzico di anagrafica per il frontend. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponseDTO {

    /** Token Bearer; il browser lo tiene e lo rimanda sulle API private. */
    private String token;

    /** ACQUIRENTE, VENDITORE o ADMIN: la UI sceglie menu e guard. */
    private String ruolo;

    private String nome;

    private String cognome;

    /** Id utente per “profilo” e rotte /mie/... */
    private Long id;
}
