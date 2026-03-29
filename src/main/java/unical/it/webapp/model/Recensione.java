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
 * Voto e testo di un acquirente su un annuncio, consentito solo dopo una visita segnata come completata
 * per quella coppia acquirente–annuncio.
 */
@Entity
@Table(name = "recensione")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Recensione {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // Chiave primaria della recensione: necessaria per moderazione admin e cancellazioni mirate.
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    // Commento libero dell'acquirente: arricchisce il dettaglio annuncio oltre al solo voto.
    private String testo;

    @Column(nullable = false)
    // Punteggio (es. 1–5): sintesi rapida della soddisfazione per liste e medie future.
    private Integer voto;

    @Column(name = "created_at", nullable = false)
    // Momento della pubblicazione: ordina le recensioni e impedisce ambiguità temporali.
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acquirente_id", nullable = false)
    // Utente che ha scritto la recensione (tipicamente ruolo ACQUIRENTE): ManyToOne perché uno può recensire più annunci.
    private Utente acquirente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "annuncio_id", nullable = false)
    // Annuncio recensito: lato inverso della OneToMany su Annuncio.recensioni.
    private Annuncio annuncio;
}
