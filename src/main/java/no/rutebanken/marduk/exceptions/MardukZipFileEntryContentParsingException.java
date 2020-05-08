package no.rutebanken.marduk.exceptions;

public class MardukZipFileEntryContentParsingException extends FileValidationException {

    public MardukZipFileEntryContentParsingException(Throwable cause) {
        super(cause);
    }

    public MardukZipFileEntryContentParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
