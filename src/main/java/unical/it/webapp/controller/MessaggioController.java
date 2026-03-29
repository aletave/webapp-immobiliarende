package unical.it.webapp.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
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
import unical.it.webapp.dao.MessaggioDAO;
import unical.it.webapp.dao.UtenteDAO;
import unical.it.webapp.dto.MessaggioNonLettiRiepilogoDTO;
import unical.it.webapp.dto.MessaggioRequestDTO;
import unical.it.webapp.dto.MessaggioResponseDTO;
import unical.it.webapp.dto.MessaggioUpdateDTO;
import unical.it.webapp.model.Annuncio;
import unical.it.webapp.model.Messaggio;
import unical.it.webapp.model.Utente;

/**
 * REST per messaggi di contatto su annuncio: invio solo ACQUIRENTE autenticato (nome/email da DB), lettura per il venditore proprietario.
 */
@RestController
@RequestMapping("/api/messaggi")
@RequiredArgsConstructor
public class MessaggioController {

    private final MessaggioDAO messaggioDAO;

    private final AnnuncioDAO annuncioDAO;

    private final UtenteDAO utenteDAO;

    /** Invia messaggio su annuncio. Solo acquirente; mittente da profilo. */
    @PostMapping
    @PreAuthorize("hasRole('ACQUIRENTE')")
    public ResponseEntity<Void> create(@RequestBody MessaggioRequestDTO request) {
        if (request.getAnnuncioId() == null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<Annuncio> opt = annuncioDAO.findById(request.getAnnuncioId());
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (request.getTesto() == null || request.getTesto().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String authEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<Utente> utenteOpt = utenteDAO.findByEmail(authEmail);
        if (utenteOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Utente u = utenteOpt.get();
        String nomeDisplay = nomeMittenteDaUtente(u);

        Messaggio nuovo = Messaggio.builder()
                .nomeMittente(nomeDisplay)
                .emailMittente(u.getEmail().trim())
                .testo(request.getTesto().trim())
                .annuncio(opt.get())
                .createdAt(LocalDateTime.now())
                .lettoVenditore(false)
                .build();
        messaggioDAO.save(nuovo);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** Messaggi di un annuncio. Solo il venditore titolare li vede e li marca letti. */
    @GetMapping("/annuncio/{id}")
    @PreAuthorize("hasRole('VENDITORE') or hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<List<MessaggioResponseDTO>> listByAnnuncio(@PathVariable Long id) {
        Optional<Annuncio> opt = annuncioDAO.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Annuncio annuncio = opt.get();
        Long userId = getCurrentUserId();
        if (!isOwner(annuncio, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        messaggioDAO.segnaLettiVenditorePerAnnuncio(id);
        List<Messaggio> lista = messaggioDAO.findByAnnuncio_Id(id);
        lista.sort(Comparator.comparing(Messaggio::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        List<MessaggioResponseDTO> out = new ArrayList<>();
        for (Messaggio m : lista) {
            out.add(toResponse(m));
        }
        return ResponseEntity.ok(out);
    }

    /** Conteggio non letti per annuncio. Venditore. */
    @GetMapping("/venditore/riepilogo-non-letti")
    @PreAuthorize("hasRole('VENDITORE') or hasRole('ADMIN')")
    public ResponseEntity<List<MessaggioNonLettiRiepilogoDTO>> riepilogoNonLettiVenditore() {
        Long userId = getCurrentUserId();
        List<Object[]> rows = messaggioDAO.countNonLettiGroupByAnnuncioPerVenditore(userId);
        List<MessaggioNonLettiRiepilogoDTO> out = new ArrayList<>();
        for (Object[] row : rows) {
            Long annuncioId = (Long) row[0];
            long count = ((Number) row[1]).longValue();
            if (count <= 0) {
                continue;
            }
            Optional<Annuncio> aOpt = annuncioDAO.findById(annuncioId);
            if (aOpt.isEmpty()) {
                continue;
            }
            out.add(MessaggioNonLettiRiepilogoDTO.builder()
                    .annuncioId(annuncioId)
                    .titoloAnnuncio(aOpt.get().getTitolo())
                    .numNonLetti(count)
                    .build());
        }
        return ResponseEntity.ok(out);
    }

    /** Messaggi inviati dal tuo account (email nel token). Acquirente. */
    @GetMapping("/miei")
    @PreAuthorize("hasRole('ACQUIRENTE')")
    public ResponseEntity<List<MessaggioResponseDTO>> mieiMessaggi() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        List<Messaggio> lista = messaggioDAO.findByEmailMittenteWithAnnuncio(email);
        List<MessaggioResponseDTO> out = new ArrayList<>();
        for (Messaggio m : lista) {
            out.add(toResponse(m, true));
        }
        return ResponseEntity.ok(out);
    }

    /** Modifica testo se sei il mittente (email token = email messaggio). */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ACQUIRENTE')")
    public ResponseEntity<MessaggioResponseDTO> update(
            @PathVariable Long id, @RequestBody MessaggioUpdateDTO body) {
        if (body.getTesto() == null || body.getTesto().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Optional<Messaggio> opt = messaggioDAO.findByIdWithAnnuncio(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Messaggio m = opt.get();
        String authEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!isMittenteEmail(m, authEmail)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<Utente> utenteOpt = utenteDAO.findByEmail(authEmail);
        if (utenteOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        m.setNomeMittente(nomeMittenteDaUtente(utenteOpt.get()));
        m.setTesto(body.getTesto().trim());
        Messaggio salvato = messaggioDAO.save(m);
        return ResponseEntity.ok(toResponse(salvato, true));
    }

    /** Cancellazione: mittente acquirente o venditore dell'annuncio (o admin). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ACQUIRENTE','VENDITORE','ADMIN')")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Optional<Messaggio> opt = messaggioDAO.findByIdWithAnnuncio(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Messaggio m = opt.get();
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean roleAcq =
                auth.getAuthorities().stream().anyMatch(a -> "ROLE_ACQUIRENTE".equals(a.getAuthority()));
        boolean roleVen =
                auth.getAuthorities().stream().anyMatch(a -> "ROLE_VENDITORE".equals(a.getAuthority()));
        boolean roleAdmin =
                auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        String authEmail = auth.getName();

        if (roleAcq) {
            if (!isMittenteEmail(m, authEmail)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            messaggioDAO.delete(m);
            return ResponseEntity.noContent().build();
        }
        if (roleVen || roleAdmin) {
            Long userId = getCurrentUserId();
            Annuncio ann = m.getAnnuncio();
            if (ann == null || !isOwner(ann, userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            messaggioDAO.delete(m);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    private boolean isMittenteEmail(Messaggio m, String authEmail) {
        if (m.getEmailMittente() == null || authEmail == null) {
            return false;
        }
        return m.getEmailMittente().trim().equalsIgnoreCase(authEmail.trim());
    }

    /** Nome e cognome dal profilo; se assenti, fallback sull’email. */
    private String nomeMittenteDaUtente(Utente u) {
        String n = u.getNome() != null ? u.getNome().trim() : "";
        String c = u.getCognome() != null ? u.getCognome().trim() : "";
        String t = (n + " " + c).trim();
        return t.isEmpty() ? u.getEmail().trim() : t;
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

    private MessaggioResponseDTO toResponse(Messaggio m) {
        return toResponse(m, false);
    }

    private MessaggioResponseDTO toResponse(Messaggio m, boolean includeAnnuncioTitolo) {
        String titolo = null;
        if (includeAnnuncioTitolo && m.getAnnuncio() != null) {
            titolo = m.getAnnuncio().getTitolo();
        }
        return MessaggioResponseDTO.builder()
                .id(m.getId())
                .nomeMittente(m.getNomeMittente())
                .emailMittente(m.getEmailMittente())
                .testo(m.getTesto())
                .annuncioId(m.getAnnuncio() != null ? m.getAnnuncio().getId() : null)
                .annuncioTitolo(titolo)
                .lettoVenditore(m.isLettoVenditore())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
