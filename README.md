# ImmobiliaRende

Piattaforma web per **annunci immobiliari** nella zona di **Rende** (Cosenza): catalogo pubblico, aree riservate per venditori e acquirenti, pannello amministratore, messaggistica, richieste di visita, aste con offerte e recensioni legate alle visite completate.

Il nome è un gioco di parole tra *immobiliare* e *Rende*.

**Corso:** Web Applications — Università della Calabria, A.A. 2025/26  
**Tipologia:** progetto individuale — appello 31 marzo 2026  

---

## Cosa contiene il progetto

- **Backend** REST in Java 17 con Spring Boot 3: sicurezza JWT, validazione sui DTO, accesso dati con Spring Data JPA su PostgreSQL.
- **Frontend** single-page in Angular (~21) con Bootstrap 5: routing con guard per autenticazione e ruolo, interceptor che allega il token alle richieste HTTP.
- **Funzionalità trasversali:** geocoding indirizzi lato server (Nominatim / OpenStreetMap), caricamento foto annunci con normalizzazione (Thumbnailator) e storage in database, mappe (Leaflet e embed), grafici in dashboard admin (Chart.js).

Il backend espone le API sotto il prefisso **`/api`**. Il frontend in sviluppo è pensato per dialogare con il backend su `http://localhost:8080` (configurabile).

---

## Stack tecnologico

| Layer | Tecnologia |
|-------|------------|
| Frontend | Angular ~21.2, Bootstrap 5.3, TypeScript ~5.9 |
| Backend | Spring Boot 3.5.12, Java 17 |
| Persistenza | Spring Data JPA (Hibernate), PostgreSQL |
| Autenticazione | JWT (jjwt 0.12.6), Spring Security |
| Mappe | Google Maps (embed), Leaflet.js 1.9 |
| Grafici | Chart.js 4.x |
| Geocoding | Nominatim (backend) |

---

## Prerequisiti

- **Java 17+** (`JAVA_HOME` impostato)
- **Node.js 18+** e **npm**
- **PostgreSQL** (es. `localhost:5432`, database `webapp_exam` come negli esempi di configurazione)
- **Angular CLI** (opzionale): si può usare `npx ng serve` dalla cartella `frontend`

---

## Configurazione

1. **Database.** Creare il database (nome coerente con `spring.datasource.url` in `application.properties`), ad esempio:

   ```sql
   CREATE DATABASE webapp_exam;
   ```

2. **Applicazione Spring.** Il repo include `src/main/resources/application.properties` per lo sviluppo locale. Per partire da un modello senza segreti, copiare:

   ```text
   src/main/resources/application.properties.example  →  src/main/resources/application.properties
   ```

   Impostare almeno:

   - `spring.datasource.username` / `spring.datasource.password`
   - `jwt.secret` (stringa lunga e casuale per la firma HS256)
   - `app.server-url` se il backend non è su `http://localhost:8080` (serve per costruire gli URL pubblici delle foto servite da `/api/foto/{id}/file`)

   > **Nota:** in un ambiente reale le credenziali andrebbero fornite solo tramite variabili d’ambiente o un secret store, non committate. Il file locale facilita la riproduzione del progetto in sede d’esame.

   > **Per chi corregge il progetto:** la password PostgreSQL nel file commitato è quella della macchina dello studente. Sul vostro PC va sostituita con la password del vostro utente DB, altrimenti Spring Boot non si avvia.

3. **(Opzionale) Dati da script SQL.** Se è presente `db/dump.sql`, si può importare dopo aver creato il database:

   ```bash
   psql -U postgres -d webapp_exam -f db/dump.sql
   ```

   Senza dump, uno schema vuoto va bene: con `spring.jpa.hibernate.ddl-auto=update` Hibernate crea/aggiorna le tabelle e `src/main/resources/data.sql` esegue il seed minimo (utente admin, in modo idempotente con `ON CONFLICT`). Lo stesso file può inserire `appuntamento` in stato `COMPLETATO` dove servono per allineare recensioni esistentí alla regola «recensione solo dopo visita».

---

## Avvio

### Backend

Dalla radice del repository:

```bash
mvn spring-boot:run
```

Oppure, con il wrapper Maven incluso:

```bash
./mvnw spring-boot:run    # Linux / macOS / Git Bash
```

```powershell
mvnw.cmd spring-boot:run   # Windows (PowerShell o cmd)
```

API base: **`http://localhost:8080`** (percorsi sotto `/api/...`).

### Frontend

```bash
cd frontend
npm install
ng serve
# oppure: npx ng serve
```

Interfaccia: **`http://localhost:4200`**.

### Test backend

```bash
mvn test
```

Esegue i test JUnit presenti sotto `src/test/java` (utilità JWT, servizi, proxy annuncio, gestione errori, ecc.).

---

## Ruoli e credenziali di prova

Gli account qui sotto presuppongono **dati di esempio** nel database (dump, script aggiuntivi o registrazione manuale). Con il solo `data.sql` è garantito almeno l’**admin**; gli altri profili vanno creati o importati.

| Ruolo | Email | Password |
|-------|-------|----------|
| Admin | admin@webapp.com | password123 |
| Venditore/Admin | venditore@webapp.com | password123 |
| Venditore | venditore2@webapp.com | password123 |
| Acquirente | acquirente@webapp.com | password123 |
| Acquirente | acquirente2@webapp.com | password123 |
| Acquirente (bannato) | acquirente3@webapp.com | password123 |

Il profilo `venditore@webapp.com` può essere stato promosso ad admin pur mantenendo annunci pubblicati (utile per verificare la dashboard admin con annunci «propri»). L’utente `acquirente3@webapp.com` è **bannato** e serve a verificare il blocco al login.

La registrazione pubblica consente solo **VENDITORE** e **ACQUIRENTE**; il ruolo **ADMIN** non è auto-registrabile.

---

## Funzionalità principali

### Visitatori e utenti autenticati (catalogo)

- Elenco annunci con filtri (tipo, categoria) e ordinamento
- Dettaglio annuncio: galleria, mappa, recensioni, eventuale asta
- Mappa globale degli annunci (`/mappa`)
- Registrazione venditore o acquirente

### Venditore

- CRUD annunci, upload foto (bytes in PostgreSQL dopo ottimizzazione lato server), geocoding dell’indirizzo
- Ribasso prezzo (prezzo precedente mostrato barrato)
- Creazione asta con scadenza
- Messaggi ricevuti per annuncio
- Condivisione link annuncio (es. social)

### Acquirente

- Ricerca e filtri
- Messaggi al venditore
- Offerte su asta aperta
- Una recensione per annuncio, solo dopo visita completata
- Dashboard personale (aste, messaggi, recensioni)

### Amministratore

- Statistiche (Chart.js)
- Gestione utenti: elenco, ban/sban, promozione ad admin
- Moderazione: eliminazione annunci e recensioni; elenco e rimozione aste
- Richieste visita: in `GET /api/appuntamenti/venditore` l’admin vede **tutte** le richieste; il venditore vede solo le proprie

---

## Route Angular (estratto)

| Percorso | Contenuto |
|----------|-----------|
| `/home` | Catalogo e filtri |
| `/mappa` | Mappa Leaflet |
| `/login`, `/register` | Autenticazione |
| `/annunci/:id` | Dettaglio |
| `/dashboard/acquirente` | Area acquirente (JWT + ruolo) |
| `/dashboard/venditore` | Area venditore |
| `/dashboard/admin` | Area amministratore |

---

## Architettura backend

```text
src/main/java/unical/it/webapp/
├── controller/     REST (/api/auth, /api/annunci, /api/foto, …)
├── model/          Entità JPA
├── dao/            Spring Data JPA
├── dto/            Request/response API
├── proxy/          AnnuncioProxy (foto / caricamento controllato)
├── security/       SecurityConfig, JwtAuthFilter, JwtUtil, UserDetailsServiceImpl, CorsConfig
├── service/        GeocodingService, AccountService, AnnuncioRibassoService, FotoUrlService, …
└── exception/      GlobalExceptionHandler (risposte errore uniformi, validazione @Valid)
```

**Linee guida:** repository per entità, DTO per i contratti REST, validazione con Bean Validation dove applicabile, regole dipendenti dallo stato (es. offerte su asta) nel service/controller. `AnnuncioProxy` aiuta a evitare problemi di lazy loading fuori transazione in letture aggregate.

### Prefissi REST (`/api`)

| Prefisso | Ambito |
|----------|--------|
| `/api/auth` | Registrazione, login |
| `/api/account` | Profilo utente autenticato |
| `/api/annunci` | Annunci, ricerca, ribasso |
| `/api/foto` | Foto (upload, URL, file binario) |
| `/api/geocoding` | Ricerca indirizzi |
| `/api/messaggi` | Messaggi su annuncio |
| `/api/appuntamenti` | Richieste di visita |
| `/api/recensioni` | Recensioni |
| `/api/aste` | Aste e offerte |
| `/api/admin` | Operazioni amministrative |

---

## Struttura del repository

```text
webapp/
├── pom.xml                 # Maven, Spring Boot
├── mvnw, mvnw.cmd          # Maven Wrapper
├── README.md
├── src/main/java/          # Backend
├── src/main/resources/
│   ├── application.properties          # configurazione locale (vedi nota sopra)
│   ├── application.properties.example   # template
│   └── data.sql                        # seed admin e script di coerenza
├── src/test/java/          # Test automatici backend
├── frontend/               # Angular
│   ├── public/             # asset statici (es. hero/login)
│   └── scripts/            # es. optimize-public-images (Sharp)
└── db/                     # opzionale: es. dump.sql per import manuale
```

---

## Note tecniche aggiuntive

- **`data.sql`:** eseguito all’avvio se configurato (`spring.sql.init.mode`); include aggiornamenti idempotenti al check su `appuntamento.stato`, seed admin e, se necessario, righe `appuntamento` per allineare recensioni già presenti.
- **Foto annunci:** upload multipart; validazione tipo (JPEG, PNG, GIF, WebP) e dimensione; Thumbnailator ridimensiona fino a 1920×1080, JPEG qualità 0,8, persistenza in `bytea`; `GET /api/foto/{id}/file` con cache HTTP; nei JSON l’URL assoluto usa `app.server-url` e `FotoUrlService`. È supportato anche l’inserimento tramite URL esterno per casi legacy o test.
- **UI statiche:** immagini in `frontend/public/`; opzionale `npm run optimize:public-images` nella cartella `frontend`.
- **Multipart / Tomcat:** limiti in `application.properties` allineati a upload fino a ~20 MB per file e dimensione richiesta complessiva adeguata, per evitare troncamenti silenziosi.

---

## Consegna e riproduzione

Per valutare il progetto: configurare PostgreSQL e `application.properties`, avviare backend e frontend come sopra, verificare login per i tre ruoli e i flussi principali (annunci, messaggi, visite, recensioni, aste). In caso di problemi di connessione al database, controllare sempre **username**, **password** e **nome database** rispetto all’installazione locale.
