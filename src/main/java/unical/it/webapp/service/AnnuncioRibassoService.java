package unical.it.webapp.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unical.it.webapp.dao.AnnuncioDAO;

/** Quando scade un ribasso a tempo, rimette il prezzo di prima (chiamato anche da uno schedulato). */
@Service
@RequiredArgsConstructor
public class AnnuncioRibassoService {

    private final AnnuncioDAO annuncioDAO;

    @Transactional
    public void ripristinaRibassiScaduti() {
        annuncioDAO.ripristinaRibassiScaduti(LocalDateTime.now());
    }

    @Transactional
    @Scheduled(fixedRate = 300_000)
    public void ripristinaRibassiScadutiSchedulato() {
        ripristinaRibassiScaduti();
    }
}
