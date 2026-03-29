package unical.it.webapp.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import unical.it.webapp.model.Appuntamento;
import unical.it.webapp.model.StatoAppuntamento;

/**
 * Richieste di visita. Nel tempo ci possono essere più righe per stessa coppia acquirente–annuncio;
 * quella “più recente” è quella utile alla UI.
 */
public interface AppuntamentoDAO extends JpaRepository<Appuntamento, Long> {

    /** Ultima richiesta per quella coppia annuncio/acquirente. */
    Optional<Appuntamento> findTopByAcquirenteIdAndAnnuncioIdOrderByCreatedAtDesc(
            Long acquirenteId, Long annuncioId);

    boolean existsByAcquirenteIdAndAnnuncioIdAndStato(
            Long acquirenteId, Long annuncioId, StatoAppuntamento stato);

    /** Richieste sugli annunci di un venditore, dalla più recente. */
    @Query(
            "SELECT DISTINCT a FROM Appuntamento a "
                    + "JOIN FETCH a.acquirente acq "
                    + "JOIN FETCH a.annuncio ann "
                    + "WHERE ann.venditore.id = :venditoreId "
                    + "ORDER BY a.createdAt DESC")
    List<Appuntamento> findByAnnuncioVenditoreIdOrderByCreatedAtDesc(@Param("venditoreId") Long venditoreId);

    /** Tutte le richieste (vista admin). */
    @Query(
            "SELECT DISTINCT a FROM Appuntamento a "
                    + "JOIN FETCH a.acquirente acq "
                    + "JOIN FETCH a.annuncio ann "
                    + "ORDER BY a.createdAt DESC")
    List<Appuntamento> findAllOrderByCreatedAtDesc();

    /** Storico lato acquirente. */
    @Query(
            "SELECT DISTINCT a FROM Appuntamento a "
                    + "JOIN FETCH a.annuncio ann "
                    + "WHERE a.acquirente.id = :acquirenteId "
                    + "ORDER BY a.createdAt DESC")
    List<Appuntamento> findByAcquirenteIdOrderByCreatedAtDesc(@Param("acquirenteId") Long acquirenteId);

    /**
     * Una riga per id con grafo esplicito. Così evitiamo DISTINCT + troppi fetch che a volte
     * duplicavano il risultato e Spring si lamentava.
     */
    @EntityGraph(attributePaths = {"acquirente", "annuncio", "annuncio.venditore"})
    @Query("SELECT a FROM Appuntamento a WHERE a.id = :id")
    Optional<Appuntamento> findByIdWithAnnuncioAndVenditore(@Param("id") Long id);
}
