package no.rutebanken.marduk.pubsub;

import com.google.api.gax.rpc.AlreadyExistsException;
import org.apache.camel.*;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gcp.pubsub.PubSubAdmin;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;

@UriEndpoint(firstVersion = "0.1", scheme = "entur-google-pubsub", title = "Entur Google Pubsub",
        syntax = "entur-google-pubsub:destinationName", label = "messaging")
public class EnturGooglePubSubEndpoint extends DefaultEndpoint {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @UriPath(description = "Destination Name")
    @Metadata(required = "true")
    private String destinationName;

    @UriParam(name = "concurrentConsumers", description = "The number of parallel streams consuming from the subscription", defaultValue = "1")
    private Integer concurrentConsumers = 1;

    @UriParam(defaultValue = "AUTO", enums = "AUTO,NONE",
            description = "AUTO = exchange gets ack'ed/nack'ed on completion. NONE = downstream process has to ack/nack explicitly")
    private EnturGooglePubSubConstants.AckMode ackMode = EnturGooglePubSubConstants.AckMode.AUTO;

    private String projectId;
    private PubSubTemplate pubSubTemplate;
    private PubSubAdmin pubSubAdmin;

    public EnturGooglePubSubEndpoint(String uri, Component component, String remaining, PubSubTemplate pubSubTemplate, PubSubAdmin pubSubAdmin) {
        super(uri, component);
        if (!(component instanceof EnturGooglePubSubComponent)) {
            throw new IllegalArgumentException("The component provided is not EnturGooglePubSubComponent : " + component.getClass().getName());
        }
        this.pubSubTemplate = pubSubTemplate;
        this.pubSubAdmin = pubSubAdmin;
        setExchangePattern(ExchangePattern.InOnly);
    }


    @Override
    public Consumer createConsumer(Processor processor) {
        return new EnturGooglePubSubConsumer(this, processor, pubSubTemplate);
    }

    @Override
    public Producer createProducer() {
        return new EnturGooglePubSubProducer(this, pubSubTemplate);
    }

    public void createSubscriptionIfMissing() {

        try {
            pubSubAdmin.createTopic(getDestinationName());
            logger.debug("Created topic: " + getDestinationName());
        } catch (AlreadyExistsException e) {
            logger.trace("Did not create topic: " + getDestinationName() + " ,as it already exists");
        }

        try {
            pubSubAdmin.createSubscription(getDestinationName(), getDestinationName());
            logger.debug("Created subscription: " + getDestinationName());
        } catch (AlreadyExistsException e) {
            logger.trace("Did not create subscription: " + getDestinationName() + " ,as it already exists");
        }

    }

    public String getDestinationName() {
        return destinationName;
    }

    public void setDestinationName(String destinationName) {
        this.destinationName = destinationName;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public EnturGooglePubSubConstants.AckMode getAckMode() {
        return ackMode;
    }

    public Integer getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public void setConcurrentConsumers(Integer concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    @Override
    public boolean isSingleton() {
        return false;
    }
}

