-- Hibernate @Version: righe con version NULL mandano in errore il commit (NPE in Versioning.increment).
UPDATE asta SET version = 0 WHERE version IS NULL;

-- Vincolo legacy: alcuni DB hanno appuntamento_stato_check solo con PENDING/COMPLETATO (manca RIFIUTATO).
-- Allineamento a StatoAppuntamento.java e PUT /api/appuntamenti/{id}/rifiuta.
ALTER TABLE appuntamento DROP CONSTRAINT IF EXISTS appuntamento_stato_check;
ALTER TABLE appuntamento ADD CONSTRAINT appuntamento_stato_check
    CHECK (stato::text IN ('PENDING', 'COMPLETATO', 'RIFIUTATO'));

INSERT INTO utente (email, password, ruolo, nome, cognome, bannato)
VALUES ('admin@webapp.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LzTFufhL1Gy', 'ADMIN', 'Admin', 'Sistema', false)
ON CONFLICT (email) DO NOTHING;

-- Coerenza con la regola «recensione solo dopo visita COMPLETATA»: per ogni riga già presente in
-- recensione (seed, dump locale o dati di test) crea un appuntamento COMPLETATO con data antecedente
-- alla recensione, così la demo non mostra recensioni «orfane». Idempotente: non inserisce duplicati COMPLETATO.
INSERT INTO appuntamento (annuncio_id, acquirente_id, stato, created_at)
SELECT r.annuncio_id,
       r.acquirente_id,
       'COMPLETATO',
       r.created_at - INTERVAL '1 day'
FROM recensione r
WHERE NOT EXISTS (
    SELECT 1
    FROM appuntamento a
    WHERE a.annuncio_id = r.annuncio_id
      AND a.acquirente_id = r.acquirente_id
      AND a.stato = 'COMPLETATO'
);
