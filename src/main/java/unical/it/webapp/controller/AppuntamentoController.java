package unical.it.webapp.controller;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import unical.it.webapp.dao.AnnuncioDAO;
import unical.it.webapp.dao.AppuntamentoDAO;
import unical.it.webapp.dao.UtenteDAO;
import unical.it.webapp.dto.AppuntamentoRequestDTO;
import unical.it.webapp.dto.AppuntamentoResponseDTO;
import unical.it.webapp.model.Annuncio;
import unical.it.webapp.model.Appuntamento;
import unical.it.webapp.model.StatoAppuntamento;
import unical.it.webapp.model.Utente;

/**
 * REST per richieste di visita: creazione da acquirente, elenco e completamento o rifiuto da venditore/admin.
 */
@RestController
@RequestMapping("/api/appuntamenti")
@RequiredArgsConstructor
public class AppuntamentoController {

    private final AppuntamentoDAO appuntamentoDAO;
    private final AnnuncioDAO annuncioDAO;
    private final UtenteDAO utenteDAO;

    /** Nuova richiesta visita in attesa. Acquirente. */
    @PostMapping
    @PreAuthorize("hasRole('ACQUIRENTE')")
    public ResponseEntity<?> create(@Valid @RequestBody AppuntamentoRequestDTO request) {
        if (request.getAnnuncioId() == null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<Annuncio> opt = annuncioDAO.findById(request.getAnnuncioId());
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Annuncio annuncio = opt.get();
        Utente acquirente = utenteDAO
                .findByEmail(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow(() -> new IllegalStateException("Utente autenticato non trovato nel database"));

        if (annuncio.getVenditore() != null && annuncio.getVenditore().getId().equals(acquirente.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Non puoi richiedere una visita sul tuo stesso annuncio."));
        }

        if (appuntamentoDAO.existsByAcquirenteIdAndAnnuncioIdAndStato(
                acquirente.getId(), annuncio.getId(), StatoAppuntamento.PENDING)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Hai già una richiesta di visita in attesa per questo annuncio."));
        }

        Appuntamento nuovo = Appuntamento.builder()
                .annuncio(annuncio)
                .acquirente(acquirente)
                .stato(StatoAppuntamento.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        Appuntamento salvato = appuntamentoDAO.save(nuovo);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(salvato));
    }

    /**
     * Elenco cronologico di tutte le richieste di visita dell’acquirente autenticato.
     */
    @GetMapping("/miei")
    @PreAuthorize("hasRole('ACQUIRENTE')")
    public ResponseEntity<List<AppuntamentoResponseDTO>> listMiei() {
        Utente acquirente = utenteDAO
                .findByEmail(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow(() -> new IllegalStateException("Utente autenticato non trovato nel database"));
        List<Appuntamento> lista =
                appuntamentoDAO.findByAcquirenteIdOrderByCreatedAtDesc(acquirente.getId());
        List<AppuntamentoResponseDTO> out = new ArrayList<>();
        for (Appuntamento a : lista) {
            out.add(toResponse(a));
        }
        return ResponseEntity.ok(out);
    }

    /**
     * Stato più recente della visita per l’acquirente su questo annuncio (ultimo record per data creazione).
     */
    @GetMapping("/mio-per-annuncio/{annuncioId}")
    @PreAuthorize("hasRole('ACQUIRENTE')")
    public ResponseEntity<AppuntamentoResponseDTO> mioPerAnnuncio(@PathVariable Long annuncioId) {
        Utente acquirente = utenteDAO
                .findByEmail(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow(() -> new IllegalStateException("Utente autenticato non trovato nel database"));
        return appuntamentoDAO
                .findTopByAcquirenteIdAndAnnuncioIdOrderByCreatedAtDesc(acquirente.getId(), annuncioId)
                .map(a -> ResponseEntity.ok(toResponse(a)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Venditore vede le richieste sui propri annunci; admin vede tutte.
     */
    @GetMapping("/venditore")
    @PreAuthorize("hasRole('VENDITORE') or hasRole('ADMIN')")
    public ResponseEntity<List<AppuntamentoResponseDTO>> listPerVenditore() {
        boolean admin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        Utente utente = utenteDAO
                .findByEmail(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow(() -> new IllegalStateException("Utente autenticato non trovato nel database"));
        List<Appuntamento> lista =
                admin
                        ? appuntamentoDAO.findAllOrderByCreatedAtDesc()
                        : appuntamentoDAO.findByAnnuncioVenditoreIdOrderByCreatedAtDesc(utente.getId());
        List<AppuntamentoResponseDTO> out = new ArrayList<>();
        for (Appuntamento a : lista) {
            out.add(toResponse(a));
        }
        return ResponseEntity.ok(out);
    }

    /**
     * Il venditore proprietario dell'annuncio collegato segna la visita come completata.
     */
    @PutMapping("/{id}/completa")
    @PreAuthorize("hasRole('VENDITORE') or hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<?> completa(@PathVariable Long id) {
        Optional<Appuntamento> opt = appuntamentoDAO.findByIdWithAnnuncioAndVenditore(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Appuntamento app = opt.get();
        if (app.getStato() != StatoAppuntamento.PENDING) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "La richiesta non è in stato attesa."));
        }
        Optional<Utente> venditoreOpt = utenteDAO.findByEmail(
                SecurityContextHolder.getContext().getAuthentication().getName());
        if (venditoreOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Utente venditore = venditoreOpt.get();
        Annuncio ann = app.getAnnuncio();
        if (ann == null || ann.getVenditore() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        boolean admin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!admin && !ann.getVenditore().getId().equals(venditore.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        app.setStato(StatoAppuntamento.COMPLETATO);
        appuntamentoDAO.save(app);
        return ResponseEntity.ok(toResponse(app));
    }

    /**
     * Il venditore proprietario dell'annuncio collegato (o un admin) rifiuta la richiesta di visita.
     */
    @PutMapping("/{id}/rifiuta")
    @PreAuthorize("hasRole('VENDITORE') or hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<?> rifiuta(@PathVariable Long id) {
        Optional<Appuntamento> opt = appuntamentoDAO.findByIdWithAnnuncioAndVenditore(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Appuntamento app = opt.get();
        if (app.getStato() != StatoAppuntamento.PENDING) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "La richiesta non è in stato attesa."));
        }
        Optional<Utente> venditoreOpt = utenteDAO.findByEmail(
                SecurityContextHolder.getContext().getAuthentication().getName());
        if (venditoreOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Utente venditore = venditoreOpt.get();
        Annuncio ann = app.getAnnuncio();
        if (ann == null || ann.getVenditore() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        boolean admin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!admin && !ann.getVenditore().getId().equals(venditore.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        app.setStato(StatoAppuntamento.RIFIUTATO);
        appuntamentoDAO.save(app);
        return ResponseEntity.ok(toResponse(app));
    }

    private AppuntamentoResponseDTO toResponse(Appuntamento a) {
        Utente acq = a.getAcquirente();
        Annuncio ann = a.getAnnuncio();
        return AppuntamentoResponseDTO.builder()
                .id(a.getId())
                .annuncioId(ann != null ? ann.getId() : null)
                .titoloAnnuncio(ann != null ? ann.getTitolo() : null)
                .stato(a.getStato())
                .createdAt(a.getCreatedAt())
                .acquirenteId(acq != null ? acq.getId() : null)
                .nomeAcquirente(acq != null ? acq.getNome() : null)
                .cognomeAcquirente(acq != null ? acq.getCognome() : null)
                .emailAcquirente(acq != null ? acq.getEmail() : null)
                .build();
    }
}
