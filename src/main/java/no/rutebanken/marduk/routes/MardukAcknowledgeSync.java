package no.rutebanken.marduk.routes;

import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.pubsub.v1.AcknowledgeRequest;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.camel.spi.Synchronization;

import java.util.ArrayList;
import java.util.List;

public class MardukAcknowledgeSync implements Synchronization {

    private static final String PROP_MESSAGES = "RutebankenPropMessages";

    private final SubscriberStub subscriber;
    private final String subscriptionName;

    public MardukAcknowledgeSync(SubscriberStub subscriber, String subscriptionName) {
        this.subscriber = subscriber;
        this.subscriptionName = subscriptionName;
    }

    @Override
    public void onComplete(Exchange exchange) {
        AcknowledgeRequest ackRequest = AcknowledgeRequest.newBuilder()
                .addAllAckIds(getAckIdList(exchange))
                .setSubscription(subscriptionName).build();
        try {
            subscriber.acknowledgeCallable().call(ackRequest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onFailure(Exchange exchange) {
    }

    private List<String> getAckIdList(Exchange exchange) {
        List<String> ackList = new ArrayList<>();

        if (exchange.getIn().getBody() instanceof List) {
            for (Object body : exchange.getIn().getBody(List.class)) {
                if (body instanceof Exchange) {
                    String ackId = exchange.getIn().getHeader(GooglePubsubConstants.ACK_ID, String.class);
                    if (null != ackId) {
                        ackList.add(ackId);
                    }
                }
            }
        } else if(exchange.getProperty(PROP_MESSAGES) != null) {
            for (Object body : exchange.getProperty(PROP_MESSAGES,List.class) ) {
                if (body instanceof Message) {
                    String ackId = ((Message)body).getHeader(GooglePubsubConstants.ACK_ID, String.class);
                    if (null != ackId) {
                        ackList.add(ackId);
                    }
                }
            }
        }

        String ackId = exchange.getIn().getHeader(GooglePubsubConstants.ACK_ID, String.class);
        if (null != ackId) {
            ackList.add(ackId);
        }

        return ackList;
    }

}
