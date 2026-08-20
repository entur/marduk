package no.rutebanken.marduk.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static no.rutebanken.marduk.rest.MardukRestExceptionHandler.plainText;

/**
 * The two answers the internal admin API gives that the routes did not, kept off the partner API.
 *
 * <p>Scoped to {@link AdminRestController} on purpose. {@link AdminExternalRestController} serves the
 * published timetable-management spec, which documents 200, 401, 403, 404 and 500 and no 400: an
 * {@code IllegalArgumentException} there - a request with no file part - answered 500 under Camel and still
 * does, because a partner client is entitled to the contract it integrated against. Ninkasi is ours, so the
 * admin API can answer 400 where the route turned a typo into a 500.
 */
@RestControllerAdvice(assignableTypes = AdminRestController.class)
public class AdminRestExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadArgument(IllegalArgumentException e) {
        return plainText(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** {@code netex.import.http.autoStartup=false} left the route unstarted, which failed the send. */
    @ExceptionHandler(AdminRestController.HttpImportDisabledException.class)
    public ResponseEntity<String> handleHttpImportDisabled(AdminRestController.HttpImportDisabledException e) {
        return plainText(HttpStatus.SERVICE_UNAVAILABLE, "Upload over HTTP is disabled");
    }
}
