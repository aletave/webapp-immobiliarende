package unical.it.webapp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Una richiesta, un passaggio: legge Bearer, valida JWT e riempie il contesto Spring se tutto torna.
 * Niente sessione server, solo token.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final CorsConfigurationSource corsConfigurationSource;

    /**
     * Senza Authorization Bearer si prosegue anonimi. Con token valido si caricano i dettagli utente;
     * bannati → 403, token valido ma utente sparito → 401 con JSON, altrimenti contesto autenticato e si passa avanti.
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Solo header Authorization (HttpHeaders.AUTHORIZATION = "Authorization"): mai InputStream/Reader/parameter dal body,
        // così multipart/form-data non viene consumato prima del DispatcherServlet.
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        if (!jwtUtil.validateToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        String username = jwtUtil.extractEmail(token);
        if (username == null || username.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        } catch (DisabledException e) {
            SecurityContextHolder.clearContext();
            applyCorsHeaders(request, response);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"USER_BANNED\",\"message\":\"Utente bannato\"}");
            return;
        } catch (UsernameNotFoundException e) {
            SecurityContextHolder.clearContext();
            // Non proseguire come anonimo: altrimenti Spring Security risponde 403 senza corpo (richieste multipart incluse).
            applyCorsHeaders(request, response);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"USER_NOT_FOUND\",\"message\":\"Token valido ma utente assente nel database\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Se rispondiamo dentro il filtro, MVC non aggiunge CORS e il browser lamenta. Copiamo gli header
     * dallo stesso CorsConfigurationSource del resto dell'app.
     */
    private void applyCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        CorsConfiguration config = corsConfigurationSource.getCorsConfiguration(request);
        if (config == null) {
            return;
        }
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null) {
            return;
        }
        String resolved = config.checkOrigin(origin);
        if (resolved == null) {
            return;
        }
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, resolved);
        if (Boolean.TRUE.equals(config.getAllowCredentials())) {
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }
    }
}
