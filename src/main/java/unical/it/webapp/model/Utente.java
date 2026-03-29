package unical.it.webapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rappresenta un utente registrato sulla piattaforma (admin, venditore o acquirente).
 * È l'entità centrale per l'autenticazione JWT e per collegare annunci, recensioni e aste alle persone reali.
 */
@Entity
@Table(name = "utente")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Utente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // Identificativo univoco nel database: serve a Hibernate e alle foreign key delle altre tabelle.
    private Long id;

    @Column(nullable = false, length = 100)
    // Nome proprio mostrato in profilo e nei contatti: separato dal cognome per ordine e ricerca.
    private String nome;

    @Column(nullable = false, length = 100)
    // Cognome dell'utente: utile per intestazioni e coerenza con dati anagrafici minimi.
    private String cognome;

    @Column(nullable = false, unique = true, length = 255)
    // Email usata come username nel login e come claim nel JWT: deve essere unica per evitare duplicati.
    private String email;

    @Column(nullable = false, length = 255)
    // Password (in produzione andrebbe sempre hashata prima del salvataggio): qui memorizziamo il segreto per Spring Security.
    private String password;

    @Column(nullable = false, length = 50)
    // Ruolo applicativo (ADMIN, VENDITORE, ACQUIRENTE): determina cosa può fare l'utente sugli endpoint REST.
    private String ruolo;

    @Column(nullable = false)
    // Flag gestito dall'admin: se true l'utente non deve poter accedere anche con credenziali corrette.
    private Boolean bannato;
}
