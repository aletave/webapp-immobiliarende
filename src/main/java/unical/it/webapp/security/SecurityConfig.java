package unical.it.webapp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Sicurezza: JWT senza sessione, CORS per Angular, regole su chi può chiamare cosa.
 * EnableMethodSecurity permette @PreAuthorize sui metodi dei controller.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    private void writeJson(HttpServletResponse response, int status, Map<String, String> body) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    /**
     * CSRF spento (API JWT), sessione STATELESS, CORS dal bean. JwtAuthFilter prima del filtro username/password
     * così il contesto è già valorizzato per le richieste successive.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(
                                (request, response, accessDeniedException) -> writeJson(
                                        response,
                                        HttpServletResponse.SC_FORBIDDEN,
                                        Map.of(
                                                "error",
                                                "ACCESS_DENIED",
                                                "message",
                                                accessDeniedException.getMessage() != null
                                                        ? accessDeniedException.getMessage()
                                                        : "Accesso negato")))
                        .authenticationEntryPoint(
                                (request, response, authException) -> writeJson(
                                        response,
                                        HttpServletResponse.SC_UNAUTHORIZED,
                                        Map.of(
                                                "error",
                                                "UNAUTHORIZED",
                                                "message",
                                                authException.getMessage() != null
                                                        ? authException.getMessage()
                                                        : "Autenticazione richiesta"))))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/messaggi/miei")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/messaggi/venditore/**")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/annunci/miei")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/annunci/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/foto/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/geocoding/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/foto/upload")
                        .hasAnyRole("VENDITORE", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/recensioni/annuncio/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/aste/annuncio/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** BCrypt: salvataggio password e verifica al login. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** AuthenticationManager per il login nell'AuthController (provider + UserDetailsService). */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
