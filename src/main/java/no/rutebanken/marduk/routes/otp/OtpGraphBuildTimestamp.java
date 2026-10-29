package no.rutebanken.marduk.routes.otp;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Generates the unique timestamp identifying an OTP graph build.
 * <p>
 * The timestamp is the single source of uniqueness for everything a build produces: the remote work directory,
 * the Kubernetes job name, the published graph file, the graph build report directory and the correlation id of the
 * job events. A millisecond timestamp alone is not unique: the idle route aggregation monitor force-completes the
 * production and candidate aggregations in the same tick, so both builds start in the same millisecond. A random
 * suffix is therefore appended to the timestamp.
 * <p>
 * Format: {@code yyyyMMddHHmmssSSS-xxxxxxxx} where {@code xxxxxxxx} is 8 lowercase hexadecimal digits.
 * The timestamp prefix keeps the generated names sortable, and the characters are restricted to what a Kubernetes
 * job name accepts.
 */
public final class OtpGraphBuildTimestamp {

    /**
     * Length of the generated timestamp. Together with the longest job name prefix
     * ({@code otp2-graph-builder-netex-candidate-}) the Kubernetes job name must stay within 63 characters,
     * the maximum length of the {@code job-name} label value.
     */
    public static final int LENGTH = 26;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final SecureRandom RANDOM = new SecureRandom();

    private OtpGraphBuildTimestamp() {
    }

    public static String generate() {
        return LocalDateTime.now().format(FORMATTER) + '-' + String.format("%08x", RANDOM.nextInt());
    }
}
