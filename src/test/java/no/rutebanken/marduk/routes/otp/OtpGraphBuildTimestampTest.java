package no.rutebanken.marduk.routes.otp;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtpGraphBuildTimestampTest {

    private static final Pattern FORMAT = Pattern.compile("[0-9]{17}-[0-9a-f]{8}");

    /**
     * The longest Kubernetes job name prefix, see Otp2NetexGraphBuilder and AbstractOtp2GraphBuilder.
     */
    private static final String LONGEST_JOB_NAME_PREFIX = "otp2-graph-builder-netex-candidate-";

    private static final int KUBERNETES_LABEL_VALUE_MAX_LENGTH = 63;

    @Test
    void testFormat() {
        String timestamp = OtpGraphBuildTimestamp.generate();
        assertTrue(FORMAT.matcher(timestamp).matches(), timestamp);
        assertEquals(OtpGraphBuildTimestamp.LENGTH, timestamp.length());
    }

    @Test
    void testKubernetesJobNameLength() {
        String jobName = LONGEST_JOB_NAME_PREFIX + OtpGraphBuildTimestamp.generate();
        assertTrue(jobName.length() <= KUBERNETES_LABEL_VALUE_MAX_LENGTH, jobName);
    }

    @Test
    void testUniqueWithinTheSameMillisecond() {
        Set<String> timestamps = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertTrue(timestamps.add(OtpGraphBuildTimestamp.generate()));
        }
    }
}
