package no.rutebanken.marduk.exceptions;

public class FileValidationException extends MardukException {

	private static final long serialVersionUID = 1L;

    public FileValidationException(){ super(); }

	public FileValidationException(String message) {
        super(message);
    }

    public FileValidationException(String message, Throwable throwable){
        super(message, throwable);
    }
}