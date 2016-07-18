package no.rutebanken.marduk.exceptions;

/**
 * To be thrown when there is some checksum validation problem
 */
public class Md5ChecksumValidationException extends MardukException{

    public Md5ChecksumValidationException(String message) {
        super(message);
    }
}
