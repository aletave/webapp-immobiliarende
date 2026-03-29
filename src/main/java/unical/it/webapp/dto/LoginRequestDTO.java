package unical.it.webapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Login: email e password. In produzione va sempre su HTTPS. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDTO {

    /** Email con cui sei registrato; nel JWT finisce nel subject. */
    private String email;

    /** Plain text qui; in DB c'è solo l'hash BCrypt. */
    private String password;
}
