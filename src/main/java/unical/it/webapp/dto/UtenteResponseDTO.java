package unical.it.webapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Utente come lo vede l'admin: anagrafica, ruolo e se è bannato, mai la password. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtenteResponseDTO {

    private Long id;

    private String nome;

    private String cognome;

    private String email;

    private String ruolo;

    private Boolean bannato;
}
