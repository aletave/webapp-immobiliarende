package unical.it.webapp.controller;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import unical.it.webapp.dao.UtenteDAO;
import unical.it.webapp.dto.LoginRequestDTO;
import unical.it.webapp.dto.LoginResponseDTO;
import unical.it.webapp.dto.RegisterRequestDTO;
import unical.it.webapp.model.Utente;
import unical.it.webapp.security.JwtUtil;

/**
 * Registrazione e login pubblici. Mai l'entity Utente in risposta: solo DTO e codici HTTP chiari.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UtenteDAO utenteDAO;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    /**
     * Registrazione: email unica (409 se c'è già), ruolo solo venditore o acquirente (default acquirente),
     * password in BCrypt, utente non bannato, 201 senza token (il login è un'altra chiamata).
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequestDTO request) {
        String emailNorm =
                request.getEmail() != null ? request.getEmail().trim().toLowerCase(Locale.ROOT) : "";
        if (emailNorm.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (utenteDAO.existsByEmail(emailNorm)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        // Solo VENDITORE o ACQUIRENTE dalla registrazione pubblica; ADMIN solo tramite promuovi in admin.
        String ruoloEffettivo;
        String ruoloRichiesto = request.getRuolo();
        if (ruoloRichiesto == null || ruoloRichiesto.isBlank()) {
            ruoloEffettivo = "ACQUIRENTE";
        } else {
            String r = ruoloRichiesto.trim();
            if ("ADMIN".equalsIgnoreCase(r)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Ruolo non consentito in fase di registrazione.");
            }
            if ("VENDITORE".equalsIgnoreCase(r)) {
                ruoloEffettivo = "VENDITORE";
            } else if ("ACQUIRENTE".equalsIgnoreCase(r)) {
                ruoloEffettivo = "ACQUIRENTE";
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Ruolo non consentito in fase di registrazione.");
            }
        }

        Utente nuovo = Utente.builder()
                .nome(request.getNome())
                .cognome(request.getCognome())
                .email(emailNorm)
                .password(passwordEncoder.encode(request.getPassword()))
                .ruolo(ruoloEffettivo)
                .bannato(false)
                .build();

        utenteDAO.save(nuovo);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Login: Spring controlla password e ban; se va bene generiamo JWT e restituiamo token + dati leggeri per la UI.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO request) {
        String emailNorm =
                request.getEmail() != null ? request.getEmail().trim().toLowerCase(Locale.ROOT) : "";
        if (emailNorm.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(emailNorm, request.getPassword()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (AuthenticationException e) {
            // Spring può avvolgere DisabledException (es. InternalAuthenticationServiceException).
            if (isDisabledInChain(e)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            throw e;
        }

        Utente utente = utenteDAO
                .findByEmailIgnoreCase(emailNorm)
                .orElseThrow(() -> new IllegalStateException("Utente autenticato ma non presente nel database"));

        String token = jwtUtil.generateToken(utente.getEmail(), utente.getRuolo());

        LoginResponseDTO body = LoginResponseDTO.builder()
                .token(token)
                .ruolo(utente.getRuolo())
                .nome(utente.getNome())
                .cognome(utente.getCognome())
                .id(utente.getId())
                .build();

        return ResponseEntity.ok(body);
    }

    /** Vero se da qualche parte nella catena c'è un utente bannato (DisabledException). */
    private static boolean isDisabledInChain(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof DisabledException) {
                return true;
            }
        }
        return false;
    }
}
