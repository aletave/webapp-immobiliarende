package unical.it.webapp.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import unical.it.webapp.dao.AnnuncioDAO;
import unical.it.webapp.dao.AstaDAO;
import unical.it.webapp.dao.FotoDAO;
import unical.it.webapp.dao.UtenteDAO;
import unical.it.webapp.dto.AnnuncioRequestDTO;
import unical.it.webapp.dto.AnnuncioResponseDTO;
import unical.it.webapp.dto.FotoResponseDTO;
import unical.it.webapp.dto.RibassaRequestDTO;
import unical.it.webapp.service.AnnuncioRibassoService;
import unical.it.webapp.service.FotoUrlService;
import unical.it.webapp.service.GeocodingService;
import unical.it.webapp.model.Annuncio;
import unical.it.webapp.model.Asta;
import unical.it.webapp.model.Foto;
import unical.it.webapp.model.Utente;
import unical.it.webapp.proxy.AnnuncioProxy;

/**
 * REST per annunci immobiliari: lista pubblica con filtri, dettaglio con foto (Proxy), CRUD e ribasso per venditori.
 */
@RestController
@RequestMapping("/api/annunci")
@RequiredArgsConstructor
public class AnnuncioController {

    private final AnnuncioDAO annuncioDAO;

    private final AstaDAO astaDAO;

    private final UtenteDAO utenteDAO;

    private final FotoDAO fotoDAO;

    private final FotoUrlService fotoUrlService;

    private final GeocodingService geocodingService;

    private final AnnuncioRibassoService annuncioRibassoService;

    /**
     * Lista annunci con filtri tipo/categoria e ordinamento prezzo o mq in memoria. Pubblico, senza login.
     */
    @GetMapping
    public ResponseEntity<List<AnnuncioResponseDTO>> list(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) String direction) {

        List<Annuncio> annunci;
        boolean tipoPresente = tipo != null && !tipo.isBlank();
        boolean catPresente = categoria != null && !categoria.isBlank();

        annuncioRibassoService.ripristinaRibassiScaduti();
        if (tipoPresente && catPresente) {
            annunci = annuncioDAO.findByTipoAndCategoria(tipo.trim(), categoria.trim());
        } else if (tipoPresente) {
            annunci = annuncioDAO.findByTipo(tipo.trim());
        } else if (catPresente) {
            annunci = annuncioDAO.findByCategoria(categoria.trim());
        } else {
            annunci = annuncioDAO.findAll();
        }

        ordinaAnnunci(annunci, orderBy, direction);

        List<Long> ids = annunci.stream().map(Annuncio::getId).toList();
        Map<Long, Asta> astaByAnnuncioId = caricaAstaMap(ids);
        Map<Long, Foto> primaFotoByAnnuncioId = caricaPrimaFotoMap(ids);
        List<AnnuncioResponseDTO> out = new ArrayList<>();
        for (Annuncio a : annunci) {
            out.add(toResponseConPrimaFoto(a, astaByAnnuncioId, primaFotoByAnnuncioId));
        }
        return ResponseEntity.ok(out);
    }

    /** Annunci del venditore loggato (o admin venditore). JWT obbligatorio. */
    @GetMapping("/miei")
    @PreAuthorize("hasRole('VENDITORE') or hasRole('ADMIN')")
    public ResponseEntity<List<AnnuncioResponseDTO>> listMiei() {
        annuncioRibassoService.ripristinaRibassiScaduti();
        Long venditoreId = getCurrentUserId();
        List<Annuncio> annunci = annuncioDAO.findByVenditoreId(venditoreId);
        List<Long> ids = annunci.stream().map(Annuncio::getId).toList();
        Map<Long, Asta> astaByAnnuncioId = caricaAstaMap(ids);
        Map<Long, Foto> primaFotoByAnnuncioId = caricaPrimaFotoMap(ids);
        List<AnnuncioResponseDTO> out = new ArrayList<>();
        for (Annuncio a : annunci) {
            out.add(toResponseConPrimaFoto(a, astaByAnnuncioId, primaFotoByAnnuncioId));
        }
        return ResponseEntity.ok(out);
    }

    /** Dettaglio con foto caricate al volo via proxy. Pubblico. */
    @GetMapping("/{id}")
    public ResponseEntity<AnnuncioResponseDTO> getById(@PathVariable Long id) {
        annuncioRibassoService.ripristinaRibassiScaduti();
        Optional<Annuncio> opt = annuncioDAO.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Annuncio annuncio = opt.get();
        AnnuncioProxy proxy = new AnnuncioProxy(annuncio, fotoDAO);
        List<FotoResponseDTO> fotoDto = new ArrayList<>();
        for (Foto f : proxy.getFoto()) {
            fotoDto.add(toFotoResponse(f));
        }
        Map<Long, Asta> astaByAnnuncioId = caricaAstaMap(List.of(annuncio.getId()));
        return ResponseEntity.ok(toResponseConFoto(annuncio, fotoDto, astaByAnnuncioId));
    }

    /**
     * Crea annuncio per l'utente nel token; geocoding da indirizzo. Ruoli venditore o admin.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','VENDITORE')")
    public ResponseEntity<AnnuncioResponseDTO> create(@Valid @RequestBody AnnuncioRequestDTO request) {
        Utente venditore = utenteDAO
                .findByEmail(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow(() -> new IllegalStateException("Utente autenticato non trovato nel database"));

        double[] coords = geocodingService.geocode(request.getIndirizzo());
        Annuncio nuovo = Annuncio.builder()
                .titolo(request.getTitolo())
                .descrizione(request.getDescrizione())
                .prezzo(request.getPrezzo())
                .prezzoPrecedente(null)
                .ribassoFine(null)
                .mq(request.getMq())
                .categoria(request.getCategoria())
                .tipo(request.getTipo())
                .lat(coords[0])
                .lng(coords[1])
                .indirizzo(request.getIndirizzo().trim())
                .createdAt(LocalDateTime.now())
                .venditore(venditore)
                .build();

        Annuncio salvato = annuncioDAO.save(nuovo);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponseSenzaFoto(salvato));
    }

    /**
     * Aggiorna annuncio con nuovo geocoding. Admin o venditore proprietario; 404 se manca l'id.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','VENDITORE')")
    public ResponseEntity<AnnuncioResponseDTO> update(
            @PathVariable Long id, @Valid @RequestBody AnnuncioRequestDTO request) {
        Optional<Annuncio> opt = annuncioDAO.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Annuncio annuncio = opt.get();
        Long userId = getCurrentUserId();
        if (!isAdmin() && !isOwner(annuncio, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        double[] coords = geocodingService.geocode(request.getIndirizzo());
        Double listinoPrima = annuncio.getPrezzoPrecedente();
        annuncio.setTitolo(request.getTitolo());
        annuncio.setDescrizione(request.getDescrizione());
        annuncio.setPrezzo(request.getPrezzo());
        annuncio.setMq(request.getMq());
        annuncio.setCategoria(request.getCategoria());
        annuncio.setTipo(request.getTipo());
        annuncio.setLat(coords[0]);
        annuncio.setLng(coords[1]);
        annuncio.setIndirizzo(request.getIndirizzo().trim());
        /* Ripristino listino da form: se il prezzo inviato coincide col listino salvato, azzera stato ribasso/promo. */
        if (listinoPrima != null && prezzoUguali(request.getPrezzo(), listinoPrima)) {
            annuncio.setPrezzoPrecedente(null);
            annuncio.setRibassoFine(null);
        }

        Annuncio salvato = annuncioDAO.save(annuncio);
        return ResponseEntity.ok(toResponseSenzaFoto(salvato));
    }

    /**
     * Elimina annuncio (cascade sulle dipendenze del modello). Stessi permessi del PUT; 204 se ok.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','VENDITORE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Optional<Annuncio> opt = annuncioDAO.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Annuncio annuncio = opt.get();
        Long userId = getCurrentUserId();
        if (!isAdmin() && !isOwner(annuncio, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        annuncioDAO.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Ribassa il prezzo tenendo il vecchio per la UI barrata. Venditore titolare o admin; 400 se il nuovo non è più basso.
     */
    @PutMapping("/{id}/ribassa")
    @PreAuthorize("hasRole('VENDITORE') or hasRole('ADMIN')")
    public ResponseEntity<AnnuncioResponseDTO> ribassa(
            @PathVariable Long id, @Valid @RequestBody RibassaRequestDTO request) {
        Optional<Annuncio> opt = annuncioDAO.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Annuncio annuncio = opt.get();
        Long userId = getCurrentUserId();
        if (!isAdmin() && !isOwner(annuncio, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Double attuale = annuncio.getPrezzo();
        Double nuovo = request.getNuovoPrezzo();
        if (nuovo == null || attuale == null || nuovo >= attuale) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        LocalDateTime fine = request.getRibassoFine();
        if (fine != null && !fine.isAfter(LocalDateTime.now())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        annuncio.setPrezzoPrecedente(attuale);
        annuncio.setPrezzo(nuovo);
        annuncio.setRibassoFine(fine);
        Annuncio salvato = annuncioDAO.save(annuncio);
        return ResponseEntity.ok(toResponseSenzaFoto(salvato));
    }

    /**
     * Torna al listino salvato in prezzoPrecedente e pulisce ribasso a tempo. Utile per chiudere un promo senza rifare tutto il form.
     */
    @PutMapping("/{id}/ribassa/annulla")
    @PreAuthorize("hasRole('VENDITORE') or hasRole('ADMIN')")
    public ResponseEntity<AnnuncioResponseDTO> annullaRibasso(@PathVariable Long id) {
        Optional<Annuncio> opt = annuncioDAO.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Annuncio annuncio = opt.get();
        Long userId = getCurrentUserId();
        if (!isAdmin() && !isOwner(annuncio, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (annuncio.getPrezzoPrecedente() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        annuncio.setPrezzo(annuncio.getPrezzoPrecedente());
        annuncio.setPrezzoPrecedente(null);
        annuncio.setRibassoFine(null);
        Annuncio salvato = annuncioDAO.save(annuncio);
        return ResponseEntity.ok(toResponseSenzaFoto(salvato));
    }

    /** Ordinamento in RAM dopo le query di filtro sul DAO. */
    private void ordinaAnnunci(List<Annuncio> annunci, String orderBy, String direction) {
        if (orderBy == null || orderBy.isBlank()) {
            return;
        }
        String dir = (direction == null || direction.isBlank()) ? "asc" : direction.trim().toLowerCase();
        boolean asc = "asc".equals(dir);
        Comparator<Annuncio> comparator = null;
        if ("prezzo".equalsIgnoreCase(orderBy.trim())) {
            comparator = Comparator.comparing(Annuncio::getPrezzo, Comparator.nullsLast(Double::compareTo));
        } else if ("mq".equalsIgnoreCase(orderBy.trim())) {
            comparator = Comparator.comparing(Annuncio::getMq, Comparator.nullsLast(Integer::compareTo));
        }
        if (comparator == null) {
            return;
        }
        if (!asc) {
            comparator = comparator.reversed();
        }
        annunci.sort(comparator);
    }

    private Long getCurrentUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return utenteDAO
                .findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Utente autenticato non trovato nel database"))
                .getId();
    }

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private boolean isOwner(Annuncio annuncio, Long userId) {
        return annuncio.getVenditore() != null && userId.equals(annuncio.getVenditore().getId());
    }

    /** Confronto prezzi monetari con tolleranza centesimi (evita errori floating point). */
    private static boolean prezzoUguali(Double a, Double b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return Math.abs(a - b) < 0.005;
    }

    private AnnuncioResponseDTO toResponseSenzaFoto(Annuncio a) {
        Map<Long, Asta> astaByAnnuncioId = caricaAstaMap(List.of(a.getId()));
        return toResponseConFoto(a, Collections.emptyList(), astaByAnnuncioId);
    }

    /** Lista home/dashboard: una sola foto (la prima per id) per anteprima card. */
    private AnnuncioResponseDTO toResponseConPrimaFoto(
            Annuncio a,
            Map<Long, Asta> astaByAnnuncioId,
            Map<Long, Foto> primaFotoByAnnuncioId) {
        Foto prima = primaFotoByAnnuncioId != null ? primaFotoByAnnuncioId.get(a.getId()) : null;
        if (prima == null) {
            return toResponseConFoto(a, Collections.emptyList(), astaByAnnuncioId);
        }
        return toResponseConFoto(a, List.of(toFotoResponse(prima)), astaByAnnuncioId);
    }

    private AnnuncioResponseDTO toResponseConFoto(
            Annuncio a, List<FotoResponseDTO> foto, Map<Long, Asta> astaByAnnuncioId) {
        Utente v = a.getVenditore();
        AnnuncioResponseDTO.AnnuncioResponseDTOBuilder b = AnnuncioResponseDTO.builder()
                .id(a.getId())
                .titolo(a.getTitolo())
                .descrizione(a.getDescrizione())
                .prezzo(a.getPrezzo())
                .prezzoPrecedente(a.getPrezzoPrecedente())
                .ribassoFine(a.getRibassoFine())
                .mq(a.getMq())
                .categoria(a.getCategoria())
                .tipo(a.getTipo())
                .lat(a.getLat())
                .lng(a.getLng())
                .indirizzo(a.getIndirizzo())
                .createdAt(a.getCreatedAt())
                .nomeVenditore(v != null ? v.getNome() : null)
                .cognomeVenditore(v != null ? v.getCognome() : null)
                .venditoreId(v != null ? v.getId() : null)
                .emailVenditore(v != null ? v.getEmail() : null)
                .foto(foto);
        applicaCampiAsta(b, a.getId(), astaByAnnuncioId);
        return b.build();
    }

    /**
     * Una query batch per le aste degli annunci richiesti (evita N+1 in lista home).
     */
    private Map<Long, Asta> caricaAstaMap(List<Long> annuncioIds) {
        if (annuncioIds == null || annuncioIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Asta> trovate = astaDAO.findByAnnuncioIds(annuncioIds);
        Map<Long, Asta> map = new HashMap<>(trovate.size());
        for (Asta asta : trovate) {
            if (asta.getAnnuncio() != null) {
                map.put(asta.getAnnuncio().getId(), asta);
            }
        }
        return map;
    }

    /**
     * Una query batch per la prima foto (id minimo) per annuncio (evita N+1 in lista home).
     */
    private Map<Long, Foto> caricaPrimaFotoMap(List<Long> annuncioIds) {
        if (annuncioIds == null || annuncioIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return fotoDAO.findPrimeByAnnuncioIdIn(annuncioIds).stream()
                .collect(Collectors.toMap(f -> f.getAnnuncio().getId(), f -> f, (a, b) -> a));
    }

    /** Campi asta in card: stessa regola dell'AstaController (aperta se ora prima della scadenza). */
    private static void applicaCampiAsta(
            AnnuncioResponseDTO.AnnuncioResponseDTOBuilder builder,
            Long annuncioId,
            Map<Long, Asta> astaByAnnuncioId) {
        Asta asta = astaByAnnuncioId != null ? astaByAnnuncioId.get(annuncioId) : null;
        if (asta == null) {
            builder.astaInCorso(false);
            return;
        }
        boolean aperta = LocalDateTime.now().isBefore(asta.getScadenza());
        builder.astaInCorso(aperta);
        if (aperta) {
            builder.astaScadenza(asta.getScadenza()).astaOffertaAttuale(asta.getOffertaAttuale());
        }
    }

    private FotoResponseDTO toFotoResponse(Foto f) {
        return FotoResponseDTO.builder()
                .id(f.getId())
                .url(fotoUrlService.publicUrl(f))
                .annuncioId(f.getAnnuncio() != null ? f.getAnnuncio().getId() : null)
                .build();
    }
}
