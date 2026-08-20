package no.rutebanken.marduk.pubsub;

import no.rutebanken.marduk.pipeline.MardukMessage;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Translation between message headers and PubSub message attributes.
 *
 * <p>Marduk's contract with the rest of the pipeline is "every header travels", which Camel implemented
 * with an {@code interceptSendToEndpoint} that copied the whole header map into the attribute map, minus
 * some exclusions. That makes the exclusion list part of the security boundary rather than housekeeping,
 * and it is reproduced here in one place so it can be tested once.
 */
public final class PubSubAttributes {

    /**
     * PubSub reserves this prefix. Publishing an attribute that starts with it fails the whole publish
     * with {@code INVALID_ARGUMENT}.
     *
     * <p>It matters because the streaming pull client delivers the delivery-attempt counter as the
     * attribute {@code googclient_deliveryattempt} whenever the subscription has a dead-letter policy, and
     * marduk re-publishes its entire header map on every {@code ChouettePollStatusQueue} reschedule. None
     * of marduk's own subscriptions has such a policy today, so nothing is broken; adding one to any of
     * them would otherwise start failing every Chouette poll. Camel's producer skipped the same prefix via
     * {@code GooglePubsubConstants.RESERVED_GOOGLE_CLIENT_ATTRIBUTE_PREFIX}, whose value is {@code "goog"}
     * and not {@code "googclient_"}.
     */
    private static final String RESERVED_PREFIX = "goog";

    /**
     * Vestigial: nothing creates {@code Camel*} headers once the framework is gone. Kept for the duration
     * of the migration, while converted and unconverted code share a message.
     */
    private static final String FRAMEWORK_PREFIX = "camel";

    /** Camel set this from {@code setUseBreadcrumb(true)}; marduk already excluded it from publishing. */
    private static final String BREADCRUMB_ID = "breadcrumbid";

    private static final String AUTHORIZATION = "authorization";

    /**
     * Camel's own limit, counted in characters. PubSub's real limit is 1024 <em>bytes</em> per value, so
     * this is very slightly the more permissive of the two for non-ASCII; kept as it was rather than
     * tightened, because tightening it would start dropping attributes that travel today.
     */
    private static final int MAX_VALUE_LENGTH = 1024;

    private PubSubAttributes() {
    }

    /**
     * The attributes to publish for a message.
     *
     * <p>Excluded: the reserved {@code goog} prefix, leftover {@code Camel} headers, {@code breadcrumbId},
     * {@code Authorization}, and any value longer than 1024 characters. The {@code Authorization}
     * exclusion is the only thing keeping a bearer token copied off an inbound HTTP request from being
     * published to a topic other services read - do not remove it. Null values become the empty string, as
     * before.
     */
    public static Map<String, String> toAttributes(MardukMessage message) {
        Map<String, String> attributes = new LinkedHashMap<>();
        message.getHeaders().forEach((name, value) -> {
            if (isPublishable(name, value)) {
                attributes.put(name, Objects.toString(value, ""));
            }
        });
        return attributes;
    }

    private static boolean isPublishable(String name, Object value) {
        String lowerCase = name.toLowerCase(Locale.ROOT);
        return !lowerCase.startsWith(RESERVED_PREFIX)
                && !lowerCase.startsWith(FRAMEWORK_PREFIX)
                && !lowerCase.equals(BREADCRUMB_ID)
                && !lowerCase.equals(AUTHORIZATION)
                && Objects.toString(value).length() <= MAX_VALUE_LENGTH;
    }

    /**
     * A message carrying an inbound PubSub payload. Every attribute becomes a header, so a value put on
     * the wire by another service - or by a previous version of marduk - is readable by name.
     *
     * <p>Nothing is filtered here. The reserved prefix is stripped on the way out instead, matching what
     * Camel's producer did, which keeps the counter readable if enforcement is ever wanted.
     */
    public static MardukMessage toMessage(byte[] payload, Map<String, String> attributes) {
        return new MardukMessage(attributes, payload);
    }
}
