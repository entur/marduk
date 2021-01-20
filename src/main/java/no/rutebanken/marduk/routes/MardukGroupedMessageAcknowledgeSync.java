package no.rutebanken.marduk.routes;

import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.pubsub.v1.AcknowledgeRequest;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.camel.spi.Synchronization;

import java.util.ArrayList;
import java.util.List;

public class MardukGroupedMessageAcknowledgeSync implements Synchronization {

    private final SubscriberStub subscriber;
    private final String subscriptionName;

    public MardukGroupedMessageAcknowledgeSync(SubscriberStub subscriber, String subscriptionName) {
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
        if (exchange.getProperty(Exchange.GROUPED_EXCHANGE) != null) {
            for (Object body : exchange.getProperty(Exchange.GROUPED_EXCHANGE, List.class)) {
                if (body instanceof Message) {
                    String ackId = ((Message) body).getHeader(GooglePubsubConstants.ACK_ID, String.class);
                    if (null != ackId) {
                        ackList.add(ackId);
                    }
                }
            }
        }
        return ackList;
    }

}
