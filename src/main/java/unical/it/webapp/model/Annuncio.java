package unical.it.webapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder.Default;

/**
 * Annuncio immobiliare (vendita o affitto): contiene i dati mostrati in lista e dettaglio,
 * la posizione sulla mappa e il collegamento al venditore che lo ha pubblicato.
 */
@Entity
@Table(name = "annuncio")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Annuncio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // Chiave primaria usata da tutte le entità collegate (foto, recensioni, asta).
    private Long id;

    @Column(nullable = false, length = 200)
    // Titolo breve in home e risultati di ricerca: primo elemento che attira l'attenzione.
    private String titolo;

    @Column(nullable = false, columnDefinition = "TEXT")
    // Descrizione estesa dell'immobile: testo libero per caratteristiche non strutturate.
    private String descrizione;

    @Column(nullable = false)
    // Prezzo corrente mostrato all'utente: base per ordinamenti e filtri economici.
    private Double prezzo;

    @Column(name = "prezzo_precedente")
    // Prezzo prima di un ribasso: serve per mostrare il prezzo barrato senza perdere lo storico.
    private Double prezzoPrecedente;

    /**
     * Fine ribasso “a tempo”: quando passa, il prezzo torna al listino salvato in prezzoPrecedente e si azzera tutto.
     * Se è null il ribasso resta finché il venditore non lo toglie a mano.
     */
    @Column(name = "ribasso_fine")
    private LocalDateTime ribassoFine;

    @Column(nullable = false)
    // Metri quadri: criterio tipico di confronto tra annunci immobiliari.
    private Integer mq;

    @Column(nullable = false, length = 100)
    // Categoria (es. appartamento, villa): permette filtri e statistiche per tipologia.
    private String categoria;

    @Column(nullable = false, length = 50)
    // Tipo di offerta (es. VENDITA o AFFITTO): distingue due modalità contrattuali diverse.
    private String tipo;

    @Column(nullable = false)
    // Latitudine per embed Google Maps: geolocalizza l'immobile sul dettaglio annuncio.
    private Double lat;

    @Column(nullable = false)
    // Longitudine: abbinata a lat definisce un punto sulla mappa.
    private Double lng;

    @Column(length = 500)
    // Testo indirizzo inserito dal venditore (geocoding lato server in lat/lng); opzionale per righe migrate senza valore.
    private String indirizzo;

    @Column(name = "created_at", nullable = false)
    // Data/ora di creazione: utile per ordinare i più recenti e per audit semplice.
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venditore_id", nullable = false)
    // Venditore proprietario dell'annuncio: ManyToOne perché un utente può avere molti annunci.
    private Utente venditore;

    @OneToMany(mappedBy = "annuncio", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Default
    // Tutte le foto collegate: cascade ALL così eliminando l'annuncio si eliminano anche le immagini; LAZY per non caricarle sempre.
    private List<Foto> foto = new ArrayList<>();

    @OneToMany(mappedBy = "annuncio", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Default
    // Recensioni lasciate sull'annuncio: stessa logica di coerenza con cascade e caricamento pigro.
    private List<Recensione> recensioni = new ArrayList<>();

    @OneToMany(mappedBy = "annuncio", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Default
    // Richieste di visita: eliminate con l'annuncio (moderazione / coerenza referenziale).
    private List<Appuntamento> appuntamenti = new ArrayList<>();
}
