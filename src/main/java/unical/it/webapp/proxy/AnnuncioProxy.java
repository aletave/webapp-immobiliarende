package unical.it.webapp.proxy;

import java.util.List;
import lombok.RequiredArgsConstructor;
import unical.it.webapp.dao.FotoDAO;
import unical.it.webapp.model.Annuncio;
import unical.it.webapp.model.Foto;

/**
 * Avanziamo le foto dell'annuncio solo alla prima getFoto(), via FotoDAO.
 * Nelle liste così non partono query immagini finché non servono.
 */
@RequiredArgsConstructor
public class AnnuncioProxy {

    private final Annuncio annuncio;
    private final FotoDAO fotoDAO;

    private List<Foto> foto;
    private boolean fotoCaricate = false;

    /**
     * Prima chiamata: carica da DB e memorizza; chiamate successive: stessa lista in memoria.
     */
    public List<Foto> getFoto() {
        if (!fotoCaricate) {
            this.foto = fotoDAO.findByAnnuncioId(annuncio.getId());
            this.fotoCaricate = true;
        }
        return this.foto;
    }
}
