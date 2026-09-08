package no.rutebanken.marduk.pipeline;

/** The policies the suite runs with, so a test does not have to spell out four numbers to get one. */
public final class RetryPolicies {

    private RetryPolicies() {
    }

    /** What {@code src/test/resources/application.properties} configures: no redelivery at all. */
    public static RetryPolicy noRetries() {
        return new RetryPolicy(0, 0, 1, 0);
    }

    /** The deployed three redeliveries, without the deployed minute of waiting. */
    public static RetryPolicy retriesWithoutWaiting() {
        return new RetryPolicy(3, 0, 1, 0);
    }
}
