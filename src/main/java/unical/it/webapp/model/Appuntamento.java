package unical.it.webapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Richiesta visita: venditore o admin possono completarla o rifiutarla; solo in stato COMPLETATO si può recensire.
 * Il DB permette più righe nel tempo per la stessa coppia, ma in app al massimo una PENDING alla volta.
 */
@Entity
@Table(name = "appuntamento")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Appuntamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "annuncio_id", nullable = false)
    private Annuncio annuncio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acquirente_id", nullable = false)
    private Utente acquirente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatoAppuntamento stato;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
