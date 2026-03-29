package unical.it.webapp.dao;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import unical.it.webapp.model.Utente;

/**
 * Utenti. Spring crea l'implementazione; qui ci sono solo le query che servono a login e registrazione.
 */
public interface UtenteDAO extends JpaRepository<Utente, Long> {

    /** Login classico per email. */
    Optional<Utente> findByEmail(String email);

    /** Stessa cosa ma senza badare alle maiuscole, così token e database restano allineati. */
    @Query("SELECT u FROM Utente u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<Utente> findByEmailIgnoreCase(@Param("email") String email);

    /** Registrazione: capire subito se l'email c'è già senza caricare la riga intera. */
    boolean existsByEmail(String email);
}
