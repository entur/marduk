package no.rutebanken.marduk.rest;

import jakarta.ws.rs.NotFoundException;
import no.rutebanken.marduk.exceptions.MardukException;
import org.junit.jupiter.api.Test;
import org.rutebanken.helper.organisation.NotAuthenticatedException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The status codes the REST routes' onException clauses returned. */
class MardukRestExceptionHandlerTest {

    private final MardukRestExceptionHandler handler = new MardukRestExceptionHandler();
    private final AdminRestExceptionHandler adminHandler = new AdminRestExceptionHandler();

    @Test
    void anExceptionAnswersWithTheStatusCodeItsRouteReturned() {
        assertEquals(403, status(handler.handleAccessDenied(new AccessDeniedException("denied"))));
        assertEquals(401, status(handler.handleNotAuthenticated(new NotAuthenticatedException("no token"))));
        assertEquals(404, status(handler.handleNotFound(new NotFoundException("unknown"))));
        assertEquals(415, status(handler.handleUnsupportedContentType(
                new HttpMediaTypeNotSupportedException(MediaType.APPLICATION_JSON, List.of()))));
        assertEquals(500, status(handler.handleFailure(new MardukException("failed"))));
    }

    @Test
    void theAdminApiAnswersTheTwoCodesTheRoutesDidNot() {
        assertEquals(400, status(adminHandler.handleBadArgument(new IllegalArgumentException("bad"))));
        assertEquals(503, status(adminHandler.handleHttpImportDisabled(
                new AdminRestController.HttpImportDisabledException())));
    }

    /**
     * Mapped globally, a bad argument would answer the partner API 400 - a code its published spec does not
     * document and Camel never returned there. The scoping is the contract, so it is asserted, not assumed.
     */
    @Test
    void aBadArgumentIsMappedForTheAdminApiAloneAndNotGlobally() {
        assertFalse(handles(MardukRestExceptionHandler.class, IllegalArgumentException.class),
                "the global advice maps IllegalArgumentException, which changes the partner API's 500 to a 400");
        assertTrue(handles(AdminRestExceptionHandler.class, IllegalArgumentException.class));
        assertEquals(List.of(AdminRestController.class),
                List.of(AdminRestExceptionHandler.class.getAnnotation(RestControllerAdvice.class)
                        .assignableTypes()),
                "the admin advice is no longer scoped to the admin controller alone");
    }

    private static boolean handles(Class<?> advice, Class<? extends Throwable> exception) {
        return Arrays.stream(advice.getDeclaredMethods())
                .map(method -> method.getAnnotation(ExceptionHandler.class))
                .filter(Objects::nonNull)
                .flatMap(annotation -> Arrays.stream(annotation.value()))
                .anyMatch(exception::equals);
    }

    private static int status(ResponseEntity<String> response) {
        return response.getStatusCode().value();
    }

    @Test
    void anErrorBodyIsPlainText() {
        // Declared as JSON it would 406 the clients that send Accept: application/json.
        assertEquals(MediaType.TEXT_PLAIN,
                handler.handleNotFound(new NotFoundException("unknown")).getHeaders().getContentType());
    }
}
