package unical.it.webapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Risultato ricerca indirizzo: lat, lng e etichetta come la restituisce Nominatim/OSM. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeocodingSearchResponseDTO {

    private double lat;

    private double lng;

    private String displayName;
}
