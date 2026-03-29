package unical.it.webapp.controller;

import jakarta.validation.Valid;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import net.coobird.thumbnailator.Thumbnails;
import unical.it.webapp.dao.AnnuncioDAO;
import unical.it.webapp.dao.FotoDAO;
import unical.it.webapp.dao.UtenteDAO;
import unical.it.webapp.dto.FotoRequestDTO;
import unical.it.webapp.dto.FotoResponseDTO;
import unical.it.webapp.model.Annuncio;
import unical.it.webapp.model.Foto;
import unical.it.webapp.proxy.AnnuncioProxy;
import unical.it.webapp.service.FotoUrlService;

/**
 * REST per le foto degli annunci: elenco via Proxy (lazy load), aggiunta ed eliminazione con controllo proprietà.
 */
@RestController
@RequestMapping("/api/foto")
@RequiredArgsConstructor
public class FotoController {

    private static final int MAX_FOTO_PER_ANNUNCIO = 10;

    /** Limite dimensione singolo file upload (allineato a spring.servlet.multipart e al client Angular). */
    private static final long MAX_BYTES = 20L * 1024 * 1024;

    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of(MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/gif", "image/webp");

    private final FotoDAO fotoDAO;

    private final AnnuncioDAO annuncioDAO;

    private final UtenteDAO utenteDAO;

    private final FotoUrlService fotoUrlService;

    /** Foto di un annuncio tramite proxy (carico lazy). Endpoint pubblico. */
    @GetMapping("/annuncio/{id}")
    public ResponseEntity<List<FotoResponseDTO>> listByAnnuncio(@PathVariable Long id) {
        Optional<Annuncio> opt = annuncioDAO.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Annuncio annuncio = opt.get();
        AnnuncioProxy proxy = new AnnuncioProxy(annuncio, fotoDAO);
        List<FotoResponseDTO> out = new ArrayList<>();
        for (Foto f : proxy.getFoto()) {
            out.add(toResponse(f));
        }
        return ResponseEntity.ok(out);
    }

    /**
     * Serve i byte dell'immagine caricata nel database (Content-Type salvato in upload).
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> getFile(@PathVariable Long id) {
        Optional<Foto> opt = fotoDAO.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Foto f = opt.get();
        if (f.getContenuto() == null || f.getContenuto().length == 0) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (f.getContentType() != null && !f.getContentType().isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(f.getContentType());
            } catch (Exception ignored) {
                // resta octet-stream
            }
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .body(f.getContenuto());
    }

    /**
     * Upload multipart: byte in DB. Ruoli controllati a mano (multipart + @PreAuthorize a volte è silenzioso).
     */
    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file, @RequestParam("annuncioId") Long annuncioId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!hasSellerOrAdminRole(auth)) {
            Map<String, Object> body = new HashMap<>();
            body.put("error", "ROLE_REQUIRED");
            body.put(
                    "authorities",
                    auth != null
                            ? auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()
                            : List.of());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }
        Optional<Annuncio> opt = annuncioDAO.findById(annuncioId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Annuncio annuncio = opt.get();
        Long userId = getCurrentUserId();
        if (!isAdmin() && annuncioDAO.countByIdAndVenditore(annuncioId, userId) == 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "error", "NOT_OWNER",
                            "annuncioId", annuncioId,
                            "userId", userId));
        }
        if (fotoDAO.countForAnnuncio(annuncioId) >= MAX_FOTO_PER_ANNUNCIO) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(java.util.Map.of(
                            "error", "LIMIT_EXCEEDED",
                            "message",
                                    "Massimo " + MAX_FOTO_PER_ANNUNCIO + " foto per annuncio"));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "EMPTY_FILE", "message", "File vuoto"));
        }
        if (file.getSize() > MAX_BYTES) {
            return ResponseEntity.badRequest()
                    .body(java.util.Map.of("error", "FILE_TOO_LARGE", "message", "File oltre 20 MB"));
        }
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_IMAGE_TYPES.contains(ct.toLowerCase(Locale.ROOT))) {
            return ResponseEntity.badRequest()
                    .body(java.util.Map.of(
                            "error",
                            "INVALID_TYPE",
                            "message",
                            "Sono ammessi solo JPEG, PNG, GIF o WebP"));
        }
        byte[] bytes;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Thumbnails.of(file.getInputStream())
                    .size(1920, 1080)
                    .outputFormat("jpg")
                    .outputQuality(0.8f)
                    .toOutputStream(baos);
            bytes = baos.toByteArray();
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Errore durante la lettura o la compressione dell'immagine",
                    e);
        }
        if (bytes.length == 0) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "EMPTY_FILE", "message", "File vuoto"));
        }

        Foto foto = Foto.builder()
                .url(null)
                .contentType(MediaType.IMAGE_JPEG_VALUE)
                .annuncio(annuncio)
                .build();
        foto.setContenuto(bytes);
        Foto salvata = fotoDAO.save(foto);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(salvata));
    }

    /** Aggiungi foto da URL. Venditore proprietario o admin. */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','VENDITORE')")
    public ResponseEntity<FotoResponseDTO> create(@Valid @RequestBody FotoRequestDTO request) {
        Optional<Annuncio> opt = annuncioDAO.findById(request.getAnnuncioId());
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Annuncio annuncio = opt.get();
        Long userId = getCurrentUserId();
        if (!isAdmin() && annuncioDAO.countByIdAndVenditore(request.getAnnuncioId(), userId) == 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (fotoDAO.countForAnnuncio(request.getAnnuncioId()) >= MAX_FOTO_PER_ANNUNCIO) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Foto foto = Foto.builder()
                .url(request.getUrl().trim())
                .annuncio(annuncio)
                .build();
        Foto salvata = fotoDAO.save(foto);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(salvata));
    }

    /** Cancella foto. Admin o venditore dell'annuncio. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','VENDITORE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Optional<Foto> opt = fotoDAO.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Foto foto = opt.get();
        Annuncio annuncio = foto.getAnnuncio();
        Long userId = getCurrentUserId();
        Long annuncioPk = annuncio != null ? annuncio.getId() : null;
        if (!isAdmin() && (annuncioPk == null || annuncioDAO.countByIdAndVenditore(annuncioPk, userId) == 0)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        fotoDAO.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Long getCurrentUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return utenteDAO
                .findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Utente autenticato non trovato nel database"))
                .getId();
    }

    private static boolean hasSellerOrAdminRole(Authentication auth) {
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> "ROLE_ADMIN".equals(a) || "ROLE_VENDITORE".equals(a));
    }

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private FotoResponseDTO toResponse(Foto f) {
        return FotoResponseDTO.builder()
                .id(f.getId())
                .url(fotoUrlService.publicUrl(f))
                .annuncioId(f.getAnnuncio() != null ? f.getAnnuncio().getId() : null)
                .build();
    }
}
