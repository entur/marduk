package no.rutebanken.marduk.pubsub;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gcp.pubsub.PubSubAdmin;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;

import java.util.Map;

public class EnturGooglePubSubComponent extends DefaultComponent {

    @Value("${spring.cloud.gcp.pubsub.project-id}")
    private String projectId;

    @Autowired
    private PubSubTemplate pubSubTemplate;

    @Autowired
    private PubSubAdmin pubSubAdmin;

    public EnturGooglePubSubComponent() {
        super();
    }

    public EnturGooglePubSubComponent(CamelContext context) {
        super(context);
    }


    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws
            Exception {

        EnturGooglePubSubEndpoint pubsubEndpoint = new EnturGooglePubSubEndpoint(uri, this, remaining, pubSubTemplate, pubSubAdmin);
        pubsubEndpoint.setDestinationName(remaining);
        pubsubEndpoint.setProjectId(projectId);

        setProperties(pubsubEndpoint, parameters);

        return pubsubEndpoint;
    }
}
