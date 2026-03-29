package unical.it.webapp.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Annuncio come lo vede il frontend: dati pubblici, venditore in sintesi, foto optional
 * (in lista spesso vuote, nel dettaglio le riempiamo quando serve).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnuncioResponseDTO {

    private Long id;

    private String titolo;

    private String descrizione;

    private Double prezzo;

    private Double prezzoPrecedente;

    /** Se c'è un ribasso a tempo, qui finisce; poi il job rimette il prezzo “normale”. */
    private LocalDateTime ribassoFine;

    private Integer mq;

    private String categoria;

    private String tipo;

    private Double lat;

    private Double lng;

    /** Indirizzo come stringa (il form di modifica); le coordinate sono lat/lng sopra. */
    private String indirizzo;

    private LocalDateTime createdAt;

    private String nomeVenditore;

    private String cognomeVenditore;

    private Long venditoreId;

    /** Email per contatto / mailto sul sito, non per autenticarsi. */
    private String emailVenditore;

    /** Nel dettaglio di solito piene; in elenco spesso omesse per non appesantire. */
    private List<FotoResponseDTO> foto;

    /** Vero se c'è asta attiva (stessa idea delle regole sulle offerte). Serve al badge in card. */
    private Boolean astaInCorso;

    /** Scadenza asta, ha senso solo se astaInCorso è vero. */
    private LocalDateTime astaScadenza;

    /** Miglior offerta in bacheca, solo se l'asta è ancora aperta. */
    private Double astaOffertaAttuale;
}
