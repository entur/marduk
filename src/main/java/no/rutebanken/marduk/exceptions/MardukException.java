package no.rutebanken.marduk.exceptions;

/**
 * Parent class of marduk exceptions
 */
public class MardukException extends RuntimeException {

    public MardukException(String message) {
        super( message );
    }

    public MardukException(String message, Throwable throwable) {
        super( message, throwable );
    }

}
