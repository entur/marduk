package no.rutebanken.marduk.exceptions;

import javax.xml.stream.XMLStreamException;

public class MardukZipFileEntryContentEncodingException extends FileValidationException {

    public MardukZipFileEntryContentEncodingException(Throwable cause) {
        super(cause);
    }

    public MardukZipFileEntryContentEncodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
