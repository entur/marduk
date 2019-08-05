/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package no.rutebanken.marduk.pubsub;

import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.PubsubMessage;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.spi.Synchronization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.cloud.gcp.pubsub.support.BasicAcknowledgeablePubsubMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static no.rutebanken.marduk.pubsub.EnturGooglePubSubConstants.GOOGLE_PUB_SUB_HEADER_PREFIX;

public class EnturGooglePubSubConsumer extends DefaultConsumer {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final EnturGooglePubSubEndpoint endpoint;
    private final Processor processor;
    private final Synchronization ackStrategy;
    private final PubSubTemplate pubSubTemplate;

    private List<Subscriber> subscribers = new ArrayList<>();

    public EnturGooglePubSubConsumer(EnturGooglePubSubEndpoint endpoint, Processor processor, PubSubTemplate pubSubTemplate) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.processor = processor;
        this.ackStrategy = new EnturExchangeAckTransaction();
        this.pubSubTemplate = pubSubTemplate;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        logger.info("Starting Google PubSub consumer for {}/{}", endpoint.getProjectId(), endpoint.getDestinationName());

        Consumer<BasicAcknowledgeablePubsubMessage> messageConsumer = new Consumer<BasicAcknowledgeablePubsubMessage>() {

            @Override
            public void accept(BasicAcknowledgeablePubsubMessage basicAcknowledgeablePubsubMessage) {

                PubsubMessage pubsubMessage = basicAcknowledgeablePubsubMessage.getPubsubMessage();

                byte[] body = pubsubMessage.getData().toByteArray();

                if (logger.isTraceEnabled()) {
                    logger.trace("Received message ID : {}", pubsubMessage.getMessageId());
                }

                Exchange exchange = endpoint.createExchange();
                exchange.getIn().setBody(body);

                exchange.getIn().setHeader(EnturGooglePubSubConstants.ACK_ID, basicAcknowledgeablePubsubMessage);
                exchange.getIn().setHeader(EnturGooglePubSubConstants.MESSAGE_ID, pubsubMessage.getMessageId());
                exchange.getIn().setHeader(EnturGooglePubSubConstants.PUBLISH_TIME, pubsubMessage.getPublishTime());

                Map<String, String> pubSubAttributes = pubsubMessage.getAttributesMap();
                if (pubSubAttributes != null) {
                    pubSubAttributes.entrySet()
                            .stream().filter(entry -> !entry.getKey().startsWith(GOOGLE_PUB_SUB_HEADER_PREFIX))
                            .forEach(entry -> exchange.getIn().setHeader(entry.getKey(), entry.getValue()));
                }

                if (endpoint.getAckMode() != EnturGooglePubSubConstants.AckMode.NONE) {
                    exchange.addOnCompletion(EnturGooglePubSubConsumer.this.ackStrategy);
                }

                try {
                    processor.process(exchange);
                } catch (Throwable e) {
                    exchange.setException(e);
                }
            }
        };

        for (int i = 0; i < endpoint.getConcurrentConsumers(); i++) {
            Subscriber subscriber = pubSubTemplate.subscribe(endpoint.getDestinationName(), messageConsumer);
            subscribers.add(subscriber);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        logger.info("Stopping Google PubSub consumer for {}/{}", endpoint.getProjectId(), endpoint.getDestinationName());

        for (Subscriber subscriber : subscribers) {
            subscriber.stopAsync();
        }

    }

}
