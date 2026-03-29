package unical.it.webapp.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unical.it.webapp.dao.AnnuncioDAO;
import unical.it.webapp.dao.AstaDAO;
import unical.it.webapp.dao.MessaggioDAO;
import unical.it.webapp.dao.RecensioneDAO;
import unical.it.webapp.dao.UtenteDAO;
import unical.it.webapp.model.Annuncio;
import unical.it.webapp.model.Asta;
import unical.it.webapp.model.Utente;

/** Cancella l'utente loggato e pulisce i dati collegati senza violare le foreign key. */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UtenteDAO utenteDAO;
    private final RecensioneDAO recensioneDAO;
    private final AstaDAO astaDAO;
    private final MessaggioDAO messaggioDAO;
    private final AnnuncioDAO annuncioDAO;

    @Transactional
    public void deleteCurrentUserAccount() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Utente utente =
                utenteDAO.findByEmail(email).orElseThrow(() -> new IllegalStateException("Utente non trovato"));
        Long userId = utente.getId();
        String ruolo = utente.getRuolo() != null ? utente.getRuolo().trim() : "";

        astaDAO.clearOfferenteByOfferenteId(userId);
        recensioneDAO.deleteByAcquirenteId(userId);

        if ("VENDITORE".equalsIgnoreCase(ruolo)) {
            List<Long> annuncioIds =
                    annuncioDAO.findByVenditoreId(userId).stream().map(Annuncio::getId).toList();
            for (Long annuncioId : annuncioIds) {
                messaggioDAO.deleteByAnnuncioId(annuncioId);
                List<Asta> aste = astaDAO.findByAnnuncioId(annuncioId);
                if (!aste.isEmpty()) {
                    astaDAO.deleteAll(aste);
                }
                annuncioDAO.deleteById(annuncioId);
            }
        }

        utenteDAO.delete(utente);
    }
}
