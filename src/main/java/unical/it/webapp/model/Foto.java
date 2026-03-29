package unical.it.webapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Singola immagine associata a un annuncio.
 * La relazione 1:N con Annuncio è il punto in cui il progetto userà il Proxy per il lazy loading delle foto.
 */
@Entity
@Table(name = "foto")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Foto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // Identificativo della riga foto: serve per DELETE singola e per query findByAnnuncioId nel Proxy.
    private Long id;

    @Column(length = 2048)
    // URL esterno opzionale; senza URL ma con bytes serviamo da GET /api/foto/{id}/file.
    private String url;

    // Su PostgreSQL NON usare @Lob con byte[]: Hibernate mappa su OID e i parametri INSERT finiscono fuori ordine (bytea vs bigint).
    @Column(name = "contenuto", columnDefinition = "bytea")
    // Byte immagine da upload; opzionale se usi solo url.
    private byte[] contenuto;

    @Column(length = 100)
    private String contentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "annuncio_id", nullable = false)
    // Annuncio a cui appartiene la foto: lato "molti" della relazione 1:N con mappedBy lato Annuncio.
    private Annuncio annuncio;
}
