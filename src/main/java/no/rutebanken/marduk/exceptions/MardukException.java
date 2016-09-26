package no.rutebanken.marduk.exceptions;

/**
 * Parent class of marduk exceptions
 */
public class MardukException extends RuntimeException {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

    public MardukException(){ super(); }

	public MardukException(String message) {
        super( message );
    }

    public MardukException(String message, Throwable throwable) {
        super( message, throwable );
    }

}
