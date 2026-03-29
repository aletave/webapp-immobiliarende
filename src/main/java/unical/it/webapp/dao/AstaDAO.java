package unical.it.webapp.dao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import unical.it.webapp.model.Asta;

/**
 * Aste: una per annuncio nel modello, con query esplicite quando serve join o lock.
 */
public interface AstaDAO extends JpaRepository<Asta, Long> {

    /**
     * Lock pessimistico sulla riga: due offerte contemporanee non leggono lo stesso stato
     * e poi si pestano i piedi.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Asta a WHERE a.id = :id")
    Optional<Asta> findByIdForOffertaWithLock(@Param("id") Long id);

    /** L'asta legata a un annuncio (in pratica zero o una). */
    @Query("SELECT s FROM Asta s WHERE s.annuncio.id = :annuncioId")
    List<Asta> findByAnnuncioId(@Param("annuncioId") Long annuncioId);

    /** Batch per lista home: tutte le aste i cui annunci sono in questo elenco di id. */
    @Query("SELECT a FROM Asta a JOIN FETCH a.annuncio ann WHERE ann.id IN :ids")
    List<Asta> findByAnnuncioIds(@Param("ids") Collection<Long> ids);

    /** Aste dove l'utente è al momento il miglior offerente, con annuncio per titolo in dashboard. */
    @Query("SELECT s FROM Asta s JOIN FETCH s.annuncio WHERE s.offerente.id = :offerenteId")
    List<Asta> findByOfferenteIdWithAnnuncio(@Param("offerenteId") Long offerenteId);

    /** Asta con offerente e annuncio già caricati (evita lazy durante ritiro offerta o simili). */
    @Query("SELECT DISTINCT a FROM Asta a LEFT JOIN FETCH a.offerente JOIN FETCH a.annuncio WHERE a.id = :id")
    Optional<Asta> findByIdWithOfferenteAndAnnuncio(@Param("id") Long id);

    /** Aste degli annunci di un venditore (dashboard venditore). */
    @Query(
            "SELECT DISTINCT a FROM Asta a JOIN FETCH a.annuncio ann LEFT JOIN FETCH a.offerente "
                    + "WHERE ann.venditore.id = :venditoreId")
    List<Asta> findByAnnuncioVenditoreIdWithAnnuncioAndOfferente(@Param("venditoreId") Long venditoreId);

    /** Panorama admin: ogni asta con annuncio, venditore e offerente. */
    @Query(
            "SELECT DISTINCT a FROM Asta a JOIN FETCH a.annuncio ann JOIN FETCH ann.venditore LEFT JOIN FETCH a.offerente")
    List<Asta> findAllForAdmin();

    /** Prima di cancellare un utente, sgancia offerente_id (è nullable). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Asta a SET a.offerente = null WHERE a.offerente.id = :uid")
    int clearOfferenteByOfferenteId(@Param("uid") Long uid);
}
