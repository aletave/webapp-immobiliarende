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
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Messaggio di contatto inviato da un visitatore al venditore tramite scheda annuncio: persiste mittente, testo e riferimento.
 */
@Entity
@Table(name = "messaggio")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Messaggio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // Chiave primaria: serve al venditore per leggere i messaggi ricevuti sul proprio annuncio.
    private Long id;

    @Column(name = "nome_mittente", nullable = false, length = 100)
    // Nome indicato dal visitatore nel form (non è necessariamente un utente registrato).
    private String nomeMittente;

    @Column(name = "email_mittente", nullable = false, length = 255)
    // Email di risposta mostrata al venditore insieme al testo.
    private String emailMittente;

    @Column(nullable = false, columnDefinition = "TEXT")
    // Corpo del messaggio: testo libero sulla richiesta di informazioni.
    private String testo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "annuncio_id", nullable = false)
    // Annuncio a cui il messaggio si riferisce: ManyToOne perché uno stesso annuncio riceve più contatti nel tempo.
    private Annuncio annuncio;

    @Column(name = "created_at", nullable = false)
    // Data/ora di invio: ordina i messaggi in lettura venditore.
    private LocalDateTime createdAt;

    @Column(name = "letto_venditore", nullable = false)
    @Builder.Default
    // True dopo che il venditore ha aperto l’elenco messaggi per l’annuncio (GET /api/messaggi/annuncio/{id}).
    private boolean lettoVenditore = false;
}
