package unical.it.webapp.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import unical.it.webapp.dao.AnnuncioDAO;
import unical.it.webapp.dao.AppuntamentoDAO;
import unical.it.webapp.dao.RecensioneDAO;
import unical.it.webapp.dao.UtenteDAO;
import unical.it.webapp.dto.RecensioneRequestDTO;
import unical.it.webapp.dto.RecensioneResponseDTO;
import unical.it.webapp.dto.RecensioneUpdateDTO;
import unical.it.webapp.model.Annuncio;
import unical.it.webapp.model.Recensione;
import unical.it.webapp.model.StatoAppuntamento;
import unical.it.webapp.model.Utente;

/**
 * REST per recensioni su annuncio: lettura pubblica, scrittura riservata agli acquirenti autenticati.
 */
@RestController
@RequestMapping("/api/recensioni")
@RequiredArgsConstructor
public class RecensioneController {

    private final RecensioneDAO recensioneDAO;

    private final AnnuncioDAO annuncioDAO;

    private final UtenteDAO utenteDAO;

    private final AppuntamentoDAO appuntamentoDAO;

    /** Recensioni di un annuncio. Pubblico. */
    @GetMapping("/annuncio/{id}")
    public ResponseEntity<List<RecensioneResponseDTO>> listByAnnuncio(@PathVariable Long id) {
        List<Recensione> lista = recensioneDAO.findByAnnuncioIdWithAcquirente(id);
        List<RecensioneResponseDTO> out = new ArrayList<>();
        for (Recensione r : lista) {
            out.add(toResponse(r));
        }
        return ResponseEntity.ok(out);
    }

    /** Recensioni sui tuoi annunci. Venditore o admin. */
    @GetMapping("/venditore")
    @PreAuthorize("hasRole('VENDITORE') or hasRole('ADMIN')")
    public ResponseEntity<List<RecensioneResponseDTO>> recensioniRicevuteVenditore() {
        Utente venditore = utenteDAO
                .findByEmail(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow(() -> new IllegalStateException("Utente autenticato non trovato nel database"));
        List<Recensione> lista = recensioneDAO.findByAnnuncioVenditoreId(venditore.getId());
        List<RecensioneResponseDTO> out = new ArrayList<>();
        for (Recensione r : lista) {
            out.add(toResponse(r, true));
        }
        return ResponseEntity.ok(out);
    }

    /** Le recensioni che hai scritto. Acquirente. */
    @GetMapping("/mie")
    @PreAuthorize("hasRole('ACQUIRENTE')")
    public ResponseEntity<List<RecensioneResponseDTO>> mieRecensioni() {
        Utente acquirente = utenteDAO
                .findByEmail(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow(() -> new IllegalStateException("Utente autenticato non trovato nel database"));
        List<Recensione> lista = recensioneDAO.findByAcquirenteIdWithAnnuncio(acquirente.getId());
        List<RecensioneResponseDTO> out = new ArrayList<>();
        for (Recensione r : lista) {
            out.add(toResponse(r, true));
        }
        return ResponseEntity.ok(out);
    }

    /**
     * Nuova recensione (solo acquirente). Serve visita completata e una sola recensione per coppia utente–annuncio.
     */
    @PostMapping
    @PreAuthorize("hasRole('ACQUIRENTE')")
    public ResponseEntity<?> create(@Valid @RequestBody RecensioneRequestDTO request) {
        Optional<Annuncio> opt = annuncioDAO.findById(request.getAnnuncioId());
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Annuncio annuncio = opt.get();
        Utente acquirente = utenteDAO
                .findByEmail(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow(() -> new IllegalStateException("Utente autenticato non trovato nel database"));

        if (recensioneDAO
                .findByAcquirenteIdAndAnnuncioId(acquirente.getId(), annuncio.getId())
                .isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Hai già lasciato una recensione per questo annuncio."));
        }

        if (!appuntamentoDAO.existsByAcquirenteIdAndAnnuncioIdAndStato(
                acquirente.getId(), annuncio.getId(), StatoAppuntamento.COMPLETATO)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "message",
                            "Puoi recensire solo dopo una visita completata dal venditore (richiesta di visita approvata)."));
        }

        Recensione nuova = Recensione.builder()
                .testo(request.getTesto().trim())
                .voto(request.getVoto())
                .createdAt(LocalDateTime.now())
                .acquirente(acquirente)
                .annuncio(annuncio)
                .build();

        Recensione salvata = recensioneDAO.save(nuova);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(salvata));
    }

    /** Modifica la tua recensione. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ACQUIRENTE')")
    public ResponseEntity<RecensioneResponseDTO> update(
            @PathVariable Long id, @RequestBody RecensioneUpdateDTO body) {
        if (body.getTesto() == null
                || body.getTesto().isBlank()
                || body.getVoto() == null
                || body.getVoto() < 1
                || body.getVoto() > 5) {
            return ResponseEntity.badRequest().build();
        }
        Optional<Recensione> opt = recensioneDAO.findByIdWithAcquirente(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Recensione r = opt.get();
        Utente acquirente = utenteDAO
                .findByEmail(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow(() -> new IllegalStateException("Utente autenticato non trovato nel database"));
        if (r.getAcquirente() == null || !r.getAcquirente().getId().equals(acquirente.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        r.setTesto(body.getTesto().trim());
        r.setVoto(body.getVoto());
        Recensione salvata = recensioneDAO.save(r);
        return ResponseEntity.ok(toResponse(salvata));
    }

    /** Elimina solo se sei l'autore. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ACQUIRENTE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Optional<Recensione> opt = recensioneDAO.findByIdWithAcquirente(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Recensione r = opt.get();
        Utente acquirente = utenteDAO
                .findByEmail(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow(() -> new IllegalStateException("Utente autenticato non trovato nel database"));
        if (r.getAcquirente() == null || !r.getAcquirente().getId().equals(acquirente.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        recensioneDAO.delete(r);
        return ResponseEntity.noContent().build();
    }

    private RecensioneResponseDTO toResponse(Recensione r) {
        return toResponse(r, false);
    }

    private RecensioneResponseDTO toResponse(Recensione r, boolean includeAnnuncioTitolo) {
        Utente acq = r.getAcquirente();
        String titolo = null;
        if (includeAnnuncioTitolo && r.getAnnuncio() != null) {
            titolo = r.getAnnuncio().getTitolo();
        }
        return RecensioneResponseDTO.builder()
                .id(r.getId())
                .testo(r.getTesto())
                .voto(r.getVoto())
                .createdAt(r.getCreatedAt())
                .nomeAcquirente(acq != null ? acq.getNome() : null)
                .cognomeAcquirente(acq != null ? acq.getCognome() : null)
                .acquirenteId(acq != null ? acq.getId() : null)
                .annuncioId(r.getAnnuncio() != null ? r.getAnnuncio().getId() : null)
                .annuncioTitolo(titolo)
                .build();
    }
}
