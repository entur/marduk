package no.rutebanken.marduk.pipeline;

import no.rutebanken.marduk.Constants;
import org.slf4j.MDC;

/**
 * The two MDC keys marduk's log pattern renders.
 *
 * <p>Camel populated these from an {@code interceptFrom(".*")} that ran at every route entry, and cleared
 * them from an {@code onCompletion}. Without a framework, whatever owns a unit of work has to do both, and
 * the clear has to be in a {@code finally}: a pooled thread that keeps a stale correlation id attributes
 * one job's log lines to another.
 */
public final class MardukMdc {

    private static final String CORRELATION_ID = "correlationId";
    private static final String CODESPACE = "codespace";

    private MardukMdc() {
    }

    /**
     * Replaces both keys from the message, exactly as {@code updateMdcFromHeaders} did: the dataset
     * referential wins over the Chouette referential when both are present, and an absent or empty value
     * leaves the key unset rather than empty.
     */
    public static void set(MardukMessage message) {
        clear();
        setCorrelationId(message.getHeader(Constants.CORRELATION_ID, String.class));
        setCodespaceIfMissing(message.getHeader(Constants.DATASET_REFERENTIAL, String.class));
        setCodespaceIfMissing(message.getHeader(Constants.CHOUETTE_REFERENTIAL, String.class));
    }

    /** Sets the correlation id, for work that starts without a message. Empty and null are ignored. */
    public static void setCorrelationId(String correlationId) {
        if (isPresent(correlationId)) {
            MDC.put(CORRELATION_ID, correlationId);
        }
    }

    /**
     * Sets the codespace only when it is not already set, replacing
     * {@code BaseRouteBuilder.setMdcCodespaceIfMissing}. Routes call this mid-flow after learning a
     * referential, and the first one learned is the one that should label the rest of the job.
     */
    public static void setCodespaceIfMissing(String codespace) {
        if (isPresent(codespace) && !isPresent(MDC.get(CODESPACE))) {
            MDC.put(CODESPACE, codespace);
        }
    }

    public static void clear() {
        MDC.remove(CORRELATION_ID);
        MDC.remove(CODESPACE);
    }

    /**
     * Runs {@code work} with the message's MDC in place, restoring whatever was there before. Nested use
     * is safe, which matters because a converted route often calls another that sets its own headers.
     */
    public static void with(MardukMessage message, Runnable work) {
        String previousCorrelationId = MDC.get(CORRELATION_ID);
        String previousCodespace = MDC.get(CODESPACE);
        try {
            set(message);
            work.run();
        } finally {
            restore(CORRELATION_ID, previousCorrelationId);
            restore(CODESPACE, previousCodespace);
        }
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isEmpty();
    }

    private static void restore(String key, String previous) {
        if (previous == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previous);
        }
    }
}
