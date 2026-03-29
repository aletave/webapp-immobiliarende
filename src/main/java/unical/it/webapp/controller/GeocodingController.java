package unical.it.webapp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import unical.it.webapp.dto.GeocodingSearchResponseDTO;
import unical.it.webapp.service.GeocodingService;

/** Geocoding pubblico per il form Angular; dietro c'è Nominatim. */
@RestController
@RequestMapping("/api/geocoding")
@RequiredArgsConstructor
public class GeocodingController {

    private final GeocodingService geocodingService;

    /** Ricerca testuale: prima corrispondenza con lat, lng, displayName. */
    @GetMapping("/search")
    public ResponseEntity<GeocodingSearchResponseDTO> search(@RequestParam("q") String q) {
        return ResponseEntity.ok(geocodingService.search(q));
    }

    /** Da coordinate a etichetta (reverse). */
    @GetMapping("/reverse")
    public ResponseEntity<GeocodingSearchResponseDTO> reverse(
            @RequestParam double lat, @RequestParam double lng) {
        return ResponseEntity.ok(geocodingService.reverse(lat, lng));
    }
}
