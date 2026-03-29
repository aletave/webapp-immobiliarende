package unical.it.webapp.dao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import unical.it.webapp.model.Foto;

/**
 * Foto annuncio. AnnuncioProxy le carica quando servono invece di tirare su tutto il grafo subito.
 */
public interface FotoDAO extends JpaRepository<Foto, Long> {

    /** Tutte le foto di un annuncio. */
    @Query("SELECT f FROM Foto f WHERE f.annuncio.id = :annuncioId")
    List<Foto> findByAnnuncioId(@Param("annuncioId") Long annuncioId);

    @Query("SELECT COUNT(f) FROM Foto f WHERE f.annuncio.id = :annuncioId")
    long countForAnnuncio(@Param("annuncioId") Long annuncioId);

    /** Una foto per annuncio (id minimo): anteprime in lista con una sola query. */
    @Query(
            "SELECT f FROM Foto f WHERE f.annuncio.id IN :ids AND f.id = ("
                    + "SELECT MIN(f2.id) FROM Foto f2 WHERE f2.annuncio.id = f.annuncio.id)")
    List<Foto> findPrimeByAnnuncioIdIn(@Param("ids") List<Long> ids);
}
