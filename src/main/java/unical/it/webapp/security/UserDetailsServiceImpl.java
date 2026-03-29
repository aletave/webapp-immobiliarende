package unical.it.webapp.security;

import java.util.Collections;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import unical.it.webapp.dao.UtenteDAO;
import unical.it.webapp.model.Utente;

/**
 * Collega le righe Utente del DB a UserDetails per login e JWT. Email = username; ruolo con prefisso ROLE_ per hasRole.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UtenteDAO utenteDAO;

    /**
     * Email come in login/JWT. Utente assente → credenziali non valide; bannato → blocco anche con password giusta.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Utente utente = utenteDAO
                .findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utente non trovato: " + email));

        if (Boolean.TRUE.equals(utente.getBannato())) {
            throw new DisabledException("Utente bannato");
        }

        String ruolo = utente.getRuolo() != null
                ? utente.getRuolo().trim().toUpperCase(Locale.ROOT)
                : "ACQUIRENTE";
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + ruolo);

        return new User(
                utente.getEmail(),
                utente.getPassword(),
                Collections.singletonList(authority));
    }
}
