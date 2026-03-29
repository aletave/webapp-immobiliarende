package unical.it.webapp.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtUtilTest {

    private static final String SECRET =
            "chiaveSegretaMoltoLungaPerFirmareITokenJWT2025TestUnical";

    private final JwtUtil jwtUtil = new JwtUtil();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", 60_000L);
    }

    @Test
    void generateThenExtractEmailAndValidate() {
        String token = jwtUtil.generateToken("user@example.com", "VENDITORE");

        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("user@example.com");
    }

    @Test
    void tamperedToken_failsValidationAndExtractReturnsNull() {
        String token = jwtUtil.generateToken("a@b.it", "ACQUIRENTE");
        String tampered = token.substring(0, token.length() - 4) + "xxxx";

        assertThat(jwtUtil.validateToken(tampered)).isFalse();
        assertThat(jwtUtil.extractEmail(tampered)).isNull();
    }

    @Test
    void expiredToken_failsValidation() {
        ReflectionTestUtils.setField(jwtUtil, "expiration", -1L);
        String token = jwtUtil.generateToken("late@x.it", "ADMIN");

        assertThat(jwtUtil.validateToken(token)).isFalse();
        assertThat(jwtUtil.extractEmail(token)).isNull();
    }

    @Test
    void wrongSigningKey_rejectsToken() {
        String token = jwtUtil.generateToken("ok@ok.it", "VENDITORE");

        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET + "ALTRO");

        assertThat(jwtUtil.validateToken(token)).isFalse();
    }
}
