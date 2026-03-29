package unical.it.webapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Nuova offerta: cifra e asta (a volte ridondante col path, ma il body resta esplicito). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OffertaRequestDTO {

    private Double importo;

    private Long astaId;
}
