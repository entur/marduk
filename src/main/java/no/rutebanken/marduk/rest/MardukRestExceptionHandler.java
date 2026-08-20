package no.rutebanken.marduk.rest;

import jakarta.ws.rs.NotFoundException;
import no.rutebanken.marduk.exceptions.MardukException;
import org.rutebanken.helper.organisation.NotAuthenticatedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * One place where an exception becomes a status code, for every endpoint.
 *
 * <p>These are the {@code onException} clauses the REST routes shared, which applied to every route in the
 * context whether or not its author thought about them. Handled per controller they drift: an endpoint whose
 * controller lacks a handler answers 500 with a stack trace where its neighbour answers 404.
 *
 * <p>Every status code here is the one the routes returned. Anything that answers differently from the
 * routes belongs in {@link AdminRestExceptionHandler}, which is scoped to the internal admin API - the
 * partner API in {@link AdminExternalRestController} answers by its published spec.
 *
 * <p>The bodies are text/plain and set on the response rather than declared, because Ninkasi sends
 * {@code Accept: application/json} everywhere and a declared {@code produces} would turn these into 406s.
 */
@RestControllerAdvice
public class MardukRestExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MardukRestExceptionHandler.class);

    /** {@code onException(AccessDeniedException)} */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDenied(AccessDeniedException e) {
        return plainText(HttpStatus.FORBIDDEN, e.getMessage());
    }

    /** {@code onException(NotAuthenticatedException)} */
    @ExceptionHandler(NotAuthenticatedException.class)
    public ResponseEntity<String> handleNotAuthenticated(NotAuthenticatedException e) {
        return plainText(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /** {@code onException(NotFoundException)} */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<String> handleNotFound(NotFoundException e) {
        return plainText(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** {@code onException(InvalidContentTypeException)}, thrown by the servlet when reading the parts. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<String> handleUnsupportedContentType(HttpMediaTypeNotSupportedException e) {
        LOGGER.error("Detected invalid content type: {}", e.getContentType());
        return plainText(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage());
    }

    @ExceptionHandler(MardukException.class)
    public ResponseEntity<String> handleFailure(MardukException e) {
        LOGGER.error("Request failed", e);
        return plainText(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    static ResponseEntity<String> plainText(HttpStatus status, String body) {
        return ResponseEntity.status(status).contentType(MediaType.TEXT_PLAIN).body(body);
    }
}
