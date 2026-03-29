package unical.it.webapp.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Firma e lettura JWT (jjwt 0.12). Secret e durata vengono da application.properties, così cambiano per ambiente.
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    /** Durata in ms (es. 86400000 = 24h), usata per la scadenza nel payload. */
    @Value("${jwt.expiration}")
    private long expiration;

    /**
     * Chiave HMAC per HS256: la stringa in UTF-8 deve avere abbastanza entropia (vedi doc jjwt).
     * Stessa chiave per firmare e per verificare.
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * JWT dopo login: subject = email, claim ruolo per le autorizzazioni; firmato e mandato al client (header Authorization).
     */
    public String generateToken(String email, String ruolo) {
        return Jwts.builder()
                .subject(email)
                .claim("ruolo", ruolo)
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Email dal subject se il token è valido (firma e scadenza); altrimenti null.
     */
    public String extractEmail(String token) {
        try {
            return parseClaims(token).getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * True solo se il parser riesce senza eccezioni (firma ok, non scaduto).
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Parsing unico condiviso tra validate ed extract. */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
