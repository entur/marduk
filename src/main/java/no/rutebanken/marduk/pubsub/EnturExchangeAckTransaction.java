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

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.spi.Synchronization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gcp.pubsub.support.BasicAcknowledgeablePubsubMessage;

import java.util.ArrayList;
import java.util.List;

public class EnturExchangeAckTransaction implements Synchronization {

    private final Logger logger = LoggerFactory.getLogger(getClass());


    @Override
    public void onComplete(Exchange exchange) {
        logger.debug("Acknowledging message after successful processing for Exchange " + exchange.getExchangeId());
        ack(getAckList(exchange));
    }


    /**
     * Negatively ack failed messages after local retry with exponential backoff.
     *
     * @param exchange
     * @see BaseRouteBuilder#configure()
     */
    @Override
    public void onFailure(Exchange exchange) {
        logger.debug("Acknowledging message after failed processing for Exchange " + exchange.getExchangeId());
        nack(getAckList(exchange));
    }


    private void ack(List<BasicAcknowledgeablePubsubMessage> ackList) {
        for (BasicAcknowledgeablePubsubMessage ack : ackList) {
            ack.ack();
        }
    }

    private void nack(List<BasicAcknowledgeablePubsubMessage> ackList) {
        for (BasicAcknowledgeablePubsubMessage ack : ackList) {
            ack.nack();
        }
    }

    private List<BasicAcknowledgeablePubsubMessage> getAckList(Exchange exchange) {
        List<BasicAcknowledgeablePubsubMessage> ackList = new ArrayList<>();

        if (null != exchange.getProperty(Exchange.GROUPED_EXCHANGE)) {
            for (Exchange ex : (List<Exchange>) exchange.getProperty(Exchange.GROUPED_EXCHANGE, List.class)) {
                BasicAcknowledgeablePubsubMessage ack = (BasicAcknowledgeablePubsubMessage) ex.getIn().getHeader(EnturGooglePubSubConstants.ACK_ID);
                if (null != ack) {
                    ackList.add(ack);
                }
            }
        } else {
            ackList.add((BasicAcknowledgeablePubsubMessage) exchange.getIn().getHeader(EnturGooglePubSubConstants.ACK_ID));
        }

        return ackList;
    }
}


