/*
 * Copyright (C) Red Hat, Inc.
 * http://www.redhat.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.poc.camelblob;

import java.io.IOException;
import java.io.InputStream;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import org.apache.activemq.ActiveMQSession;
import org.apache.activemq.BlobMessage;
import org.apache.activemq.jms.pool.PooledSession;
import org.springframework.jms.support.converter.MessageConversionException;
import org.springframework.jms.support.converter.MessageConverter;

public class BlobMessageConverter implements MessageConverter {

    @Override
    public Message toMessage(Object object, Session session) throws JMSException, MessageConversionException {
        if (session instanceof PooledSession) {
            session = ((PooledSession) session).getInternalSession();
        }
        if (session instanceof ActiveMQSession) {
            if (object instanceof InputStream) {
                return ((ActiveMQSession) session).createBlobMessage((InputStream) object);
            } else {
                throw new MessageConversionException("Object must be of type InputStream.");
            }
        } else {
            throw new MessageConversionException("Session must be of type ActiveMQSession.");
        }
    }

    @Override
    public Object fromMessage(Message message) throws JMSException, MessageConversionException {
        if (message instanceof BlobMessage) {
            try {
                return ((BlobMessage) message).getInputStream();
            } catch (IOException e) {
                JMSException jmsE = new JMSException("Unable to read blob message.");
                jmsE.setLinkedException(e);
                throw jmsE;
            }
        } else {
            throw new MessageConversionException("Message must be of type ActiveMQBlobMessage.");
        }
    }
}