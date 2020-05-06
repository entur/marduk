package no.rutebanken.marduk.exceptions;

public class MardukZipFileEntryContentEncodingException extends FileValidationException {

    public MardukZipFileEntryContentEncodingException(Throwable cause) {
        super(cause);
    }

    public MardukZipFileEntryContentEncodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
