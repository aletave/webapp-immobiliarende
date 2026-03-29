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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dati dell'asta legata a un singolo annuncio: prezzo di partenza, miglior offerta, scadenza e ultimo offerente.
 * Un annuncio ha al più un'asta, da qui la relazione OneToOne sullo stesso annuncio_id.
 */
@Entity
@Table(name = "asta")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Asta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // Identificativo dell'asta: usato nell'endpoint POST /api/aste/{id}/offerta per aggiornare l'offerta.
    private Long id;

    /** Versione per lock ottimistico sulle offerte. */
    @Version
    private Integer version;

    @Column(name = "prezzo_base", nullable = false)
    // Offerta minima iniziale fissata dal venditore: soglia sotto cui non si può partire.
    private Double prezzoBase;

    @Column(name = "offerta_attuale", nullable = false)
    // Miglior offerta corrente: viene aggiornata solo se la nuova proposta è più alta.
    private Double offertaAttuale;

    @Column(name = "offerta_ritirata", nullable = false)
    // True se l’ultimo offerente ha ritirato: l’offerta è tornata al prezzo base fino a una nuova proposta.
    private boolean offertaRitirata;

    /**
     * Ultima offerta che il venditore ha dichiarato vista in dashboard; se l'attuale è diversa,
     * in risposta mostriamo il flag nuova offerta.
     */
    @Column(name = "venditore_ultima_offerta_vista")
    private Double venditoreUltimaOffertaVista;

    @Column(nullable = false)
    // Scadenza dell'asta: dopo questa data non si accettano più offerte.
    private LocalDateTime scadenza;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offerente_id")
    // Ultimo utente che ha fatto l'offerta migliore: nullable perché all'apertura dell'asta potrebbe non esserci ancora nessuna offerta.
    private Utente offerente;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "annuncio_id", nullable = false, unique = true)
    // Annuncio univoco associato: OneToOne perché ogni asta riguarda esattamente un annuncio e viceversa (un solo record asta per annuncio).
    private Annuncio annuncio;

    /**
     * Hibernate vuole una versione non null su nuove righe. Vecchie righe con NULL vanno aggiornate a mano (vedi data.sql).
     */
    @PrePersist
    void normalizeVersionForOptimisticLock() {
        if (version == null) {
            version = 0;
        }
    }
}
