package unical.it.webapp.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import unical.it.webapp.dto.GeocodingSearchResponseDTO;

/**
 * Indirizzi con Nominatim/OSM: user-agent dichiarato e almeno un secondo tra una richiesta e l'altra (rispetto ToS).
 */
@Service
public class GeocodingService {

    private final RestTemplate restTemplate;

    /** Momento dell'ultima chiamata Nominatim (per frenare il ritmo). */
    private long ultimaRichiestaNominatimMs;

    public GeocodingService(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .defaultHeader("User-Agent", "ImmobiliaRende/1.0 (university project)")
                .additionalMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8))
                .build();
    }

    /** Indirizzo → [lat, lon]; 400 se Nominatim non trova nulla. */
    public double[] geocode(String address) {
        GeocodingSearchResponseDTO r = search(address);
        return new double[] {r.getLat(), r.getLng()};
    }

    /** Prima hit della ricerca: coordinate + display name. */
    public GeocodingSearchResponseDTO search(String query) {
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indirizzo mancante");
        }
        List<Map<String, Object>> risultati = eseguiRicercaNominatim(query.trim());
        if (risultati == null || risultati.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indirizzo non trovato: " + query);
        }
        Map<String, Object> primo = risultati.get(0);
        double lat = Double.parseDouble(String.valueOf(primo.get("lat")));
        double lon = Double.parseDouble(String.valueOf(primo.get("lon")));
        Object display = primo.get("display_name");
        String displayName = display != null ? display.toString() : query.toString();
        return new GeocodingSearchResponseDTO(lat, lon, displayName);
    }

    /** Coordinate → etichetta testuale (reverse); se manca il nome si usano lat,lng. */
    private String reverseGeocode(double lat, double lng) {
        Map<String, Object> result = eseguiReverseNominatim(lat, lng);
        if (result == null || !result.containsKey("display_name")) {
            return lat + ", " + lng;
        }
        return result.get("display_name").toString();
    }

    /** Reverse esposto come gli altri DTO di geocoding. */
    public GeocodingSearchResponseDTO reverse(double lat, double lng) {
        String displayName = reverseGeocode(lat, lng);
        return new GeocodingSearchResponseDTO(lat, lng, displayName);
    }

    private URI uriRicerca(String q) {
        return UriComponentsBuilder.fromUriString("https://nominatim.openstreetmap.org/search")
                .queryParam("q", q)
                .queryParam("format", "json")
                .queryParam("limit", 1)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
    }

    /** GET search con attesa minima tra richieste (sync su this). */
    private List<Map<String, Object>> eseguiRicercaNominatim(String q) {
        synchronized (this) {
            long now = System.currentTimeMillis();
            if (ultimaRichiestaNominatimMs > 0) {
                long attesa = 1000L - (now - ultimaRichiestaNominatimMs);
                if (attesa > 0) {
                    try {
                        Thread.sleep(attesa);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR, "Interruzione durante l'attesa verso Nominatim");
                    }
                }
            }
            URI uri = uriRicerca(q);
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            ultimaRichiestaNominatimMs = System.currentTimeMillis();
            return response.getBody();
        }
    }

    /** GET reverse: un oggetto JSON, non un array come la search. */
    private Map<String, Object> eseguiReverseNominatim(double lat, double lng) {
        synchronized (this) {
            long now = System.currentTimeMillis();
            if (ultimaRichiestaNominatimMs > 0) {
                long attesa = 1000L - (now - ultimaRichiestaNominatimMs);
                if (attesa > 0) {
                    try {
                        Thread.sleep(attesa);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR, "Interruzione durante l'attesa verso Nominatim");
                    }
                }
            }
            URI uri = UriComponentsBuilder.fromUriString("https://nominatim.openstreetmap.org/reverse")
                    .queryParam("lat", lat)
                    .queryParam("lon", lng)
                    .queryParam("format", "json")
                    .encode(StandardCharsets.UTF_8)
                    .build()
                    .toUri();
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            ultimaRichiestaNominatimMs = System.currentTimeMillis();
            Map<String, Object> body = response.getBody();
            return body != null ? body : Map.of();
        }
    }
}
