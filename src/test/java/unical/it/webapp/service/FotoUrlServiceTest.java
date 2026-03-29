package unical.it.webapp.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import unical.it.webapp.model.Foto;

class FotoUrlServiceTest {

    @Test
    void withBytesAndId_servesDownloadPath_trimsTrailingSlashOnBase() {
        FotoUrlService svc = new FotoUrlService();
        ReflectionTestUtils.setField(svc, "serverBaseUrl", "http://localhost:8080/");

        Foto f = Foto.builder().id(42L).contenuto(new byte[] {1, 2}).url("http://old").build();

        assertThat(svc.publicUrl(f)).isEqualTo("http://localhost:8080/api/foto/42/file");
    }

    @Test
    void withoutStoredBytes_fallsBackToExternalUrl() {
        FotoUrlService svc = new FotoUrlService();
        ReflectionTestUtils.setField(svc, "serverBaseUrl", "http://host:9090");

        Foto f = Foto.builder().id(99L).contenuto(null).url("https://cdn.example/img.png").build();

        assertThat(svc.publicUrl(f)).isEqualTo("https://cdn.example/img.png");
    }

    @Test
    void emptyByteArray_fallsBackToUrl() {
        FotoUrlService svc = new FotoUrlService();
        ReflectionTestUtils.setField(svc, "serverBaseUrl", "http://x");

        Foto f = Foto.builder().id(1L).contenuto(new byte[0]).url("https://only.external/a.jpg").build();

        assertThat(svc.publicUrl(f)).isEqualTo("https://only.external/a.jpg");
    }
}
