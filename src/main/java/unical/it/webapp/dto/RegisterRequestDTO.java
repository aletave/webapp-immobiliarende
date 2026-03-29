package unical.it.webapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Registrazione: nome, cognome, email, password e ruolo facoltativo (se manca → acquirente). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequestDTO {

    private String nome;

    private String cognome;

    /** Un account per email; se esiste già torniamo conflitto. */
    private String email;

    /** In database non finisce mai in chiaro, solo BCrypt. */
    private String password;

    /** VENDITORE o ACQUIRENTE; vuoto o assente e il backend mette ACQUIRENTE. */
    private String ruolo;
}
