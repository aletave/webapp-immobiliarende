package unical.it.webapp.dao;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import unical.it.webapp.model.Annuncio;

/**
 * Annunci: filtri per ricerca e dashboard, più operazioni tipo ribasso scaduto.
 * Spring Data costruisce le query dai nomi dei metodi dove non c'è @Query.
 */
public interface AnnuncioDAO extends JpaRepository<Annuncio, Long> {

    /** Solo annunci con questo tipo (VENDITA o AFFITTO). */
    List<Annuncio> findByTipo(String tipo);

    /** Solo annunci con questa categoria immobiliare. */
    List<Annuncio> findByCategoria(String categoria);

    /** Tipo e categoria insieme. */
    List<Annuncio> findByTipoAndCategoria(String tipo, String categoria);

    /**
     * Tutti gli annunci di un venditore. Serve JPQL su venditore.id perché sul modello
     * il campo è venditore, non un venditoreId scalare.
     */
    @Query("SELECT a FROM Annuncio a WHERE a.venditore.id = :venditoreId")
    List<Annuncio> findByVenditoreId(@Param("venditoreId") Long venditoreId);

    /** Conta diretta sulla tabella: utile con multipart e quando JPQL sul grafo confonde. */
    @Query(
            value = "SELECT COUNT(*) FROM annuncio WHERE id = :id AND venditore_id = :venditoreId",
            nativeQuery = true)
    long countByIdAndVenditore(@Param("id") Long id, @Param("venditoreId") Long venditoreId);

    /**
     * Quando un ribasso a tempo è scaduto, rimette il prezzo “vero” e pulisce i campi del promo.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "UPDATE Annuncio a SET a.prezzo = a.prezzoPrecedente, a.prezzoPrecedente = null, a.ribassoFine = null "
                    + "WHERE a.ribassoFine IS NOT NULL AND a.ribassoFine < :now")
    int ripristinaRibassiScaduti(@Param("now") LocalDateTime now);
}
