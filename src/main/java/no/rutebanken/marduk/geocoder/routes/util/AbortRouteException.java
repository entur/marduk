package no.rutebanken.marduk.geocoder.routes.util;

/**
 * Exception thrown to signal that route has been aborted.
 */
public class AbortRouteException extends RuntimeException {

	public AbortRouteException(String message) {
		super(message);
	}
}
