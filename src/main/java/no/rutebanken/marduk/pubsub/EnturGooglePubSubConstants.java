package no.rutebanken.marduk.pubsub;

public class EnturGooglePubSubConstants {

    public static final String GOOGLE_PUB_SUB_HEADER_PREFIX = "CamelGooglePubsub";
    /**
     * Maximum size of a PubSub attribute in bytes. See https://cloud.google.com/pubsub/quotas#other_limits
     * A String size in bytes may be greater than its length (number of characters).
     */
    public static final int GOOGLE_PUB_SUB_MAX_ATTR_LENGTH = 1024;

    public static final String MESSAGE_ID = "CamelGooglePubsub.MessageId";
    public static final String ACK_ID = "CamelGooglePubsub.MsgAckId";
    public static final String PUBLISH_TIME = "CamelGooglePubsub.PublishTime";

    public enum AckMode {
        AUTO, NONE
    }

}
