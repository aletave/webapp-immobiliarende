package unical.it.webapp.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import unical.it.webapp.model.Messaggio;

/**
 * Messaggi di contatto legati agli annunci (lato venditore soprattutto).
 */
public interface MessaggioDAO extends JpaRepository<Messaggio, Long> {

    /** Messaggi collegati a un annuncio; l'ordinamento lo fa il servizio se serve. */
    List<Messaggio> findByAnnuncio_Id(Long annuncioId);

    /** Stesso mittente (email normalizzata) con annuncio per avere il titolo in lista. */
    @Query(
            "SELECT m FROM Messaggio m JOIN FETCH m.annuncio WHERE LOWER(TRIM(m.emailMittente)) = LOWER(TRIM(:email)) ORDER BY m.createdAt DESC")
    List<Messaggio> findByEmailMittenteWithAnnuncio(@Param("email") String email);

    /** Messaggio singolo con annuncio (es. dopo modifica). */
    @Query("SELECT m FROM Messaggio m JOIN FETCH m.annuncio WHERE m.id = :id")
    Optional<Messaggio> findByIdWithAnnuncio(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Messaggio m SET m.lettoVenditore = true WHERE m.annuncio.id = :annuncioId")
    int segnaLettiVenditorePerAnnuncio(@Param("annuncioId") Long annuncioId);

    @Query(
            "SELECT m.annuncio.id, COUNT(m) FROM Messaggio m WHERE m.annuncio.venditore.id = :vid AND m.lettoVenditore = false GROUP BY m.annuncio.id")
    List<Object[]> countNonLettiGroupByAnnuncioPerVenditore(@Param("vid") Long venditoreId);

    /** L'annuncio non cancella i messaggi in cascade, quindi li togliamo a mano quando serve. */
    void deleteByAnnuncioId(Long annuncioId);
}
