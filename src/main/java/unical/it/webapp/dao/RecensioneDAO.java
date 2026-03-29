package unical.it.webapp.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import unical.it.webapp.model.Recensione;

/**
 * Recensioni su annuncio: query mirate così non carichiamo tutto il grafo JPA ogni volta.
 */
public interface RecensioneDAO extends JpaRepository<Recensione, Long> {

    /** Elenco pubblico sul dettaglio annuncio, acquirente incluso per nome (niente N+1). */
    @Query("SELECT r FROM Recensione r JOIN FETCH r.acquirente WHERE r.annuncio.id = :annuncioId")
    List<Recensione> findByAnnuncioIdWithAcquirente(@Param("annuncioId") Long annuncioId);

    /** Recensioni lasciate da un acquirente, con titolo annuncio per la sua dashboard. */
    @Query("SELECT r FROM Recensione r JOIN FETCH r.annuncio WHERE r.acquirente.id = :acquirenteId ORDER BY r.createdAt DESC")
    List<Recensione> findByAcquirenteIdWithAnnuncio(@Param("acquirenteId") Long acquirenteId);

    /** Recensioni sugli annunci di un venditore, con annuncio e acquirente. */
    @Query(
            "SELECT DISTINCT r FROM Recensione r JOIN FETCH r.annuncio a JOIN FETCH r.acquirente "
                    + "WHERE a.venditore.id = :venditoreId ORDER BY r.createdAt DESC")
    List<Recensione> findByAnnuncioVenditoreId(@Param("venditoreId") Long venditoreId);

    /** Per modifica o cancellazione: subito l'acquirente per controllare che sia davvero l'autore. */
    @Query("SELECT r FROM Recensione r JOIN FETCH r.acquirente WHERE r.id = :id")
    Optional<Recensione> findByIdWithAcquirente(@Param("id") Long id);

    /** Serve per il vincolo “una recensione per coppia acquirente–annuncio”. */
    @Query("SELECT r FROM Recensione r WHERE r.acquirente.id = :acquirenteId AND r.annuncio.id = :annuncioId")
    Optional<Recensione> findByAcquirenteIdAndAnnuncioId(
            @Param("acquirenteId") Long acquirenteId, @Param("annuncioId") Long annuncioId);

    /** Cancellazione account: via tutte le recensioni scritte da quell'utente. */
    void deleteByAcquirenteId(Long acquirenteId);
}
