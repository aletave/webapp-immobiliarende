package unical.it.webapp.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void validation_joinsFieldErrorsWithSemicolon() throws Exception {
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(new Object(), "req");
        errors.addError(new FieldError("req", "titolo", "obbligatorio"));
        errors.addError(new FieldError("req", "prezzo", "minimo non rispettato"));
        Method m = getClass().getDeclaredMethod("dummyEndpoint", String.class);
        MethodParameter param = new MethodParameter(m, 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, errors);

        ResponseEntity<Map<String, Object>> res = handler.handleValidation(ex);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).containsEntry("error", "VALIDATION_ERROR");
        assertThat(res.getBody().get("message"))
                .isEqualTo("titolo: obbligatorio; prezzo: minimo non rispettato");
    }

    @SuppressWarnings("unused")
    private void dummyEndpoint(String ignored) {}

    @Test
    void dataIntegrity_usesRootCauseMessage() {
        DataIntegrityViolationException ex =
                new DataIntegrityViolationException("wrap", new RuntimeException("unique_email"));

        ResponseEntity<Map<String, String>> res = handler.handleDataIntegrity(ex);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).containsEntry("error", "DATA_INTEGRITY");
        assertThat(res.getBody().get("message")).isEqualTo("unique_email");
    }

    @Test
    void genericHandler_unwrapsOptimisticLockToConflict() {
        ObjectOptimisticLockingFailureException root =
                new ObjectOptimisticLockingFailureException(Object.class, 1L);
        Exception wrapper = new RuntimeException("middle", root);

        ResponseEntity<Map<String, String>> res = handler.handleGenericException(wrapper);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody()).containsEntry("error", "OPTIMISTIC_LOCK");
    }
}
