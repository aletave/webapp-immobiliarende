package unical.it.webapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import unical.it.webapp.model.Foto;

/** URL pubblico della foto: se ci sono byte nel DB punta a GET /api/foto/{id}/file, altrimenti all'URL esterno. */
@Service
public class FotoUrlService {

    @Value("${app.server-url:http://localhost:8080}")
    private String serverBaseUrl;

    public String publicUrl(Foto f) {
        if (f.getContenuto() != null && f.getContenuto().length > 0 && f.getId() != null) {
            String base = serverBaseUrl.endsWith("/") ? serverBaseUrl.substring(0, serverBaseUrl.length() - 1) : serverBaseUrl;
            return base + "/api/foto/" + f.getId() + "/file";
        }
        return f.getUrl();
    }
}
