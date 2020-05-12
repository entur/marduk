package no.rutebanken.marduk.exceptions;

public class MardukZipFileEntryContentEncodingException extends MardukZipFileEntryContentParsingException {

    public MardukZipFileEntryContentEncodingException(Throwable cause) {
        super(cause);
    }

    public MardukZipFileEntryContentEncodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
