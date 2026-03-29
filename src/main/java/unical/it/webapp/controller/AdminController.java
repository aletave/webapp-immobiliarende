package unical.it.webapp.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import unical.it.webapp.dao.AnnuncioDAO;
import unical.it.webapp.dao.AstaDAO;
import unical.it.webapp.dao.RecensioneDAO;
import unical.it.webapp.dao.UtenteDAO;
import unical.it.webapp.dto.AstaResponseDTO;
import unical.it.webapp.dto.RecensioneResponseDTO;
import unical.it.webapp.dto.UtenteResponseDTO;
import unical.it.webapp.model.Annuncio;
import unical.it.webapp.model.Asta;
import unical.it.webapp.model.Recensione;
import unical.it.webapp.model.Utente;

/**
 * REST riservato agli amministratori: gestione utenti, ban, promozione e rimozione contenuti.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UtenteDAO utenteDAO;

    private final AnnuncioDAO annuncioDAO;

    private final RecensioneDAO recensioneDAO;

    private final AstaDAO astaDAO;

    /**
     * Lista utenti come DTO (niente password). Solo admin con JWT valido.
     */
    @GetMapping("/utenti")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UtenteResponseDTO>> listUtenti() {
        List<Utente> tutti = utenteDAO.findAll();
        List<UtenteResponseDTO> out = new ArrayList<>();
        for (Utente u : tutti) {
            out.add(toUtenteResponse(u));
        }
        return ResponseEntity.ok(out);
    }

    /** Banna l'utente (idempotente). Solo admin; 404 se manca l'id. */
    @PutMapping("/utenti/{id}/banna")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UtenteResponseDTO> banna(@PathVariable Long id) {
        Optional<Utente> opt = utenteDAO.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Utente u = opt.get();
        u.setBannato(true);
        Utente salvato = utenteDAO.save(u);
        return ResponseEntity.ok(toUtenteResponse(salvato));
    }

    /** Rimuove il ban. Solo admin; 404 se l'utente non c'è. */
    @PutMapping("/utenti/{id}/sbanna")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UtenteResponseDTO> sbanna(@PathVariable Long id) {
        Optional<Utente> opt = utenteDAO.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Utente u = opt.get();
        u.setBannato(false);
        Utente salvato = utenteDAO.save(u);
        return ResponseEntity.ok(toUtenteResponse(salvato));
    }

    /** Promuove a ADMIN. Solo admin già loggato; 404 se l'utente non esiste. */
    @PutMapping("/utenti/{id}/promuovi")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UtenteResponseDTO> promuovi(@PathVariable Long id) {
        Optional<Utente> opt = utenteDAO.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Utente u = opt.get();
        u.setRuolo("ADMIN");
        Utente salvato = utenteDAO.save(u);
        return ResponseEntity.ok(toUtenteResponse(salvato));
    }

    /** Cancella annuncio per id (anche senza essere il venditore). Solo admin; 404 se non c'è. */
    @DeleteMapping("/annunci/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAnnuncio(@PathVariable Long id) {
        if (!annuncioDAO.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        annuncioDAO.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /** Elimina recensione per moderazione. Solo admin; 404 se non esiste. */
    @DeleteMapping("/recensioni/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRecensione(@PathVariable Long id) {
        if (!recensioneDAO.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        recensioneDAO.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /** Tutte le aste, vista admin. */
    @GetMapping("/aste")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AstaResponseDTO>> listAste() {
        List<Asta> tutte = astaDAO.findAllForAdmin();
        List<AstaResponseDTO> out = new ArrayList<>();
        for (Asta a : tutte) {
            out.add(toAstaAdminResponse(a));
        }
        return ResponseEntity.ok(out);
    }

    /** Elimina l'asta; l'annuncio resta. Solo admin. */
    @DeleteMapping("/aste/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAsta(@PathVariable Long id) {
        if (!astaDAO.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        astaDAO.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /** Tutte le recensioni. Solo admin. */
    @GetMapping("/recensioni")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RecensioneResponseDTO>> listRecensioni() {
        List<Recensione> tutte = recensioneDAO.findAll();
        List<RecensioneResponseDTO> out = new ArrayList<>();
        for (Recensione r : tutte) {
            out.add(toRecensioneResponse(r));
        }
        return ResponseEntity.ok(out);
    }

    /** Quanti annunci per categoria (grafici). Solo admin. */
    @GetMapping("/stats/annunci-per-categoria")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> statsAnnunciPerCategoria() {
        List<Annuncio> all = annuncioDAO.findAll();
        Map<String, Long> map = all.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getCategoria() != null ? a.getCategoria() : "—",
                        Collectors.counting()));
        return ResponseEntity.ok(map);
    }

    /** Quanti annunci per tipo vendita/affitto. Solo admin. */
    @GetMapping("/stats/annunci-per-tipo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> statsAnnunciPerTipo() {
        List<Annuncio> all = annuncioDAO.findAll();
        Map<String, Long> map = all.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getTipo() != null ? a.getTipo() : "—",
                        Collectors.counting()));
        return ResponseEntity.ok(map);
    }

    private RecensioneResponseDTO toRecensioneResponse(Recensione r) {
        Utente acq = r.getAcquirente();
        return RecensioneResponseDTO.builder()
                .id(r.getId())
                .testo(r.getTesto())
                .voto(r.getVoto())
                .createdAt(r.getCreatedAt())
                .nomeAcquirente(acq != null ? acq.getNome() : null)
                .cognomeAcquirente(acq != null ? acq.getCognome() : null)
                .acquirenteId(acq != null ? acq.getId() : null)
                .annuncioId(r.getAnnuncio() != null ? r.getAnnuncio().getId() : null)
                .build();
    }

    private UtenteResponseDTO toUtenteResponse(Utente u) {
        return UtenteResponseDTO.builder()
                .id(u.getId())
                .nome(u.getNome())
                .cognome(u.getCognome())
                .email(u.getEmail())
                .ruolo(u.getRuolo())
                .bannato(u.getBannato())
                .build();
    }

    private AstaResponseDTO toAstaAdminResponse(Asta a) {
        Utente off = a.getOfferente();
        Annuncio ann = a.getAnnuncio();
        String titolo = ann != null ? ann.getTitolo() : null;
        String emailVenditore = null;
        if (ann != null && ann.getVenditore() != null) {
            emailVenditore = ann.getVenditore().getEmail();
        }
        return AstaResponseDTO.builder()
                .id(a.getId())
                .prezzoBase(a.getPrezzoBase())
                .offertaAttuale(a.getOffertaAttuale())
                .scadenza(a.getScadenza())
                .annuncioId(ann != null ? ann.getId() : null)
                .annuncioTitolo(titolo)
                .nomeOfferente(off != null ? off.getNome() : null)
                .offertaRitirata(a.isOffertaRitirata())
                .emailVenditore(emailVenditore)
                .build();
    }
}
