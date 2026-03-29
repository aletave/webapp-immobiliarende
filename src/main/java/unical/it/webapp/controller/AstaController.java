package unical.it.webapp.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import unical.it.webapp.dao.AnnuncioDAO;
import unical.it.webapp.dao.AstaDAO;
import unical.it.webapp.dao.UtenteDAO;
import unical.it.webapp.dto.AstaRequestDTO;
import unical.it.webapp.dto.AstaResponseDTO;
import unical.it.webapp.dto.OffertaRequestDTO;
import unical.it.webapp.model.Annuncio;
import unical.it.webapp.model.Asta;
import unical.it.webapp.model.Utente;

/**
 * REST per aste legate agli annunci: consultazione pubblica, creazione dal venditore, offerte dagli acquirenti.
 */
@RestController
@RequestMapping("/api/aste")
@RequiredArgsConstructor
public class AstaController {

    private final AstaDAO astaDAO;

    private final AnnuncioDAO annuncioDAO;

    private final UtenteDAO utenteDAO;

    /**
     * Aste di un annuncio; lista vuota se non ce ne sono (200, non 404) così il frontend aggrega senza esplodere.
     * Pubblico.
     */
    @GetMapping("/annuncio/{id}")
    public ResponseEntity<List<AstaResponseDTO>> getByAnnuncio(@PathVariable Long id) {
        List<Asta> trovate = astaDAO.findByAnnuncioId(id);
        if (trovate.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        List<AstaResponseDTO> out = new ArrayList<>();
        for (Asta a : trovate) {
            out.add(toResponse(a));
        }
        return ResponseEntity.ok(out);
    }

    /** Aste dove sei in testa come offerente, con titolo per la dashboard. Solo acquirente. */
    @GetMapping("/mie")
    @PreAuthorize("hasRole('ACQUIRENTE')")
    public ResponseEntity<List<AstaResponseDTO>> mieAste() {
        Long userId = getCurrentUserId();
        List<Asta> list = astaDAO.findByOfferenteIdWithAnnuncio(userId);
        List<AstaResponseDTO> out = new ArrayList<>();
        for (Asta a : list) {
            out.add(toResponse(a, true));
        }
        return ResponseEntity.ok(out);
    }

    /** Le tue aste sugli annunci che hai messo online. Venditore (o admin). */
    @GetMapping("/venditore")
    @PreAuthorize("hasRole('VENDITORE') or hasRole('ADMIN')")
    public ResponseEntity<List<AstaResponseDTO>> asteVenditore() {
        Long userId = getCurrentUserId();
        List<Asta> list = astaDAO.findByAnnuncioVenditoreIdWithAnnuncioAndOfferente(userId);
        List<AstaResponseDTO> out = new ArrayList<>();
        for (Asta a : list) {
            out.add(toResponseVenditore(a));
        }
        return ResponseEntity.ok(out);
    }

    /**
     * Apre asta (base = prima offerta). Solo se sei il venditore dell'annuncio e non c'è già un'asta. 400 se duplicata.
     */
    @PostMapping
    @PreAuthorize("hasRole('VENDITORE') or hasRole('ADMIN')")
    public ResponseEntity<AstaResponseDTO> create(@Valid @RequestBody AstaRequestDTO request) {
        Optional<Annuncio> opt = annuncioDAO.findById(request.getAnnuncioId());
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Annuncio annuncio = opt.get();
        Long userId = getCurrentUserId();
        if (!isOwner(annuncio, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!astaDAO.findByAnnuncioId(annuncio.getId()).isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        double base = request.getPrezzoBase();
        Asta nuova = Asta.builder()
                .version(0)
                .prezzoBase(base)
                .offertaAttuale(base)
                .offertaRitirata(false)
                .venditoreUltimaOffertaVista(base)
                .scadenza(request.getScadenza())
                .offerente(null)
                .annuncio(annuncio)
                .build();

        Asta salvata = astaDAO.save(nuova);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(salvata));
    }

    /**
     * Aggiorna “ultima offerta vista” su tutte le tue aste così sparisce il badge nuove offerte finché non cambia di nuovo.
     */
    @PostMapping("/venditore/segna-offerte-viste")
    @PreAuthorize("hasRole('VENDITORE') or hasRole('ADMIN')")
    public ResponseEntity<Void> segnaVenditoreOfferteViste() {
        Long userId = getCurrentUserId();
        List<Asta> list = astaDAO.findByAnnuncioVenditoreIdWithAnnuncioAndOfferente(userId);
        for (Asta a : list) {
            a.setVenditoreUltimaOffertaVista(a.getOffertaAttuale());
        }
        astaDAO.saveAll(list);
        return ResponseEntity.noContent().build();
    }

    /**
     * Nuova offerta più alta dell'attuale, con lock sulla riga. Acquirente; body astaId deve combaciare col path se presente.
     */
    @PostMapping("/{id}/offerta")
    @PreAuthorize("hasRole('ACQUIRENTE')")
    @Transactional
    public ResponseEntity<?> offerta(@PathVariable Long id, @RequestBody OffertaRequestDTO request) {
        if (request.getAstaId() != null && !request.getAstaId().equals(id)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        Optional<Asta> opt = astaDAO.findByIdForOffertaWithLock(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Asta asta = opt.get();
        if (!isAstaAperta(asta)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        Double importo = request.getImporto();
        if (importo == null || importo <= asta.getOffertaAttuale()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Utente offerente = utenteDAO
                .findByEmail(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow(() -> new IllegalStateException("Utente autenticato non trovato nel database"));

        asta.setOffertaAttuale(importo);
        asta.setOfferente(offerente);
        asta.setOffertaRitirata(false);
        try {
            Asta salvata = astaDAO.save(asta);
            return ResponseEntity.ok(toResponse(salvata));
        } catch (Exception e) {
            if (isOptimisticLockFailure(e)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of(
                                "message",
                                "Un altro utente ha appena fatto un'offerta, ricarica la pagina e riprova"));
            }
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(e);
        }
    }

    /**
     * Chi è offerente può cambiare importo (sopra base, diverso dal corrente). Asta ancora aperta.
     */
    @PutMapping("/{id}/mia-offerta")
    @PreAuthorize("hasRole('ACQUIRENTE')")
    public ResponseEntity<AstaResponseDTO> modificaMiaOfferta(@PathVariable Long id, @RequestBody OffertaRequestDTO request) {
        if (request.getAstaId() != null && !request.getAstaId().equals(id)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        Optional<Asta> opt = astaDAO.findByIdWithOfferenteAndAnnuncio(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Asta asta = opt.get();
        if (!isAstaAperta(asta)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        Long userId = getCurrentUserId();
        if (asta.getOfferente() == null || !userId.equals(asta.getOfferente().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Double importo = request.getImporto();
        if (importo == null || importo <= asta.getPrezzoBase()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        double attuale = asta.getOffertaAttuale();
        if (Math.abs(importo - attuale) < 1e-6) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        asta.setOffertaAttuale(importo);
        Asta salvata = astaDAO.save(asta);
        return ResponseEntity.ok(toResponse(salvata, true));
    }

    /** Ritiro: torni al prezzo base, nessun offerente, flag ritirata. Solo se sei l'offerente e l'asta è aperta. */
    @DeleteMapping("/{id}/mia-offerta")
    @PreAuthorize("hasRole('ACQUIRENTE')")
    public ResponseEntity<AstaResponseDTO> ritiraMiaOfferta(@PathVariable Long id) {
        Optional<Asta> opt = astaDAO.findByIdWithOfferenteAndAnnuncio(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Asta asta = opt.get();
        if (!isAstaAperta(asta)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        Long userId = getCurrentUserId();
        if (asta.getOfferente() == null || !userId.equals(asta.getOfferente().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        asta.setOffertaAttuale(asta.getPrezzoBase());
        asta.setOfferente(null);
        asta.setOffertaRitirata(true);
        Asta salvata = astaDAO.save(asta);
        return ResponseEntity.ok(toResponse(salvata, true));
    }

    /** Asta ancora aperta: ora strettamente prima della scadenza. */
    private boolean isAstaAperta(Asta asta) {
        return LocalDateTime.now().isBefore(asta.getScadenza());
    }

    private Long getCurrentUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return utenteDAO
                .findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Utente autenticato non trovato nel database"))
                .getId();
    }

    private boolean isOwner(Annuncio annuncio, Long userId) {
        return annuncio.getVenditore() != null && userId.equals(annuncio.getVenditore().getId());
    }

    private AstaResponseDTO toResponse(Asta a) {
        return toResponse(a, false);
    }

    private AstaResponseDTO toResponse(Asta a, boolean includeAnnuncioTitolo) {
        Utente off = a.getOfferente();
        String titolo = null;
        if (includeAnnuncioTitolo && a.getAnnuncio() != null) {
            titolo = a.getAnnuncio().getTitolo();
        }
        return AstaResponseDTO.builder()
                .id(a.getId())
                .prezzoBase(a.getPrezzoBase())
                .offertaAttuale(a.getOffertaAttuale())
                .scadenza(a.getScadenza())
                .annuncioId(a.getAnnuncio() != null ? a.getAnnuncio().getId() : null)
                .annuncioTitolo(titolo)
                .nomeOfferente(off != null ? off.getNome() : null)
                .offertaRitirata(a.isOffertaRitirata())
                .build();
    }

    /** Per il venditore: flag nuova offerta se il valore attuale non coincide con l'ultimo “visto” (tolleranza centesimi). */
    private AstaResponseDTO toResponseVenditore(Asta a) {
        AstaResponseDTO base = toResponse(a, true);
        base.setNuovaOfferta(nuovaOffertaPerVenditore(a));
        return base;
    }

    private static boolean nuovaOffertaPerVenditore(Asta a) {
        double attuale = a.getOffertaAttuale();
        Double visto = a.getVenditoreUltimaOffertaVista();
        if (visto == null) {
            visto = a.getPrezzoBase();
        }
        return Math.abs(attuale - visto) > 0.01;
    }

    /** Rileva conflitto di versione anche se avvolto in altre eccezioni. */
    private static boolean isOptimisticLockFailure(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ObjectOptimisticLockingFailureException) {
                return true;
            }
        }
        return false;
    }
}
