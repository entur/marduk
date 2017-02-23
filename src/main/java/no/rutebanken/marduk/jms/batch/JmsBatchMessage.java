package no.rutebanken.marduk.jms.batch;

import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.ActiveMQObjectMessage;

import javax.jms.JMSException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * ActiveMQObjectMessage wrapping a list of JMS messages.
 */
public class JmsBatchMessage extends ActiveMQObjectMessage implements Serializable {

	private List<ActiveMQMessage> messages;

	public JmsBatchMessage(List<ActiveMQMessage> messages) {
		this.messages = messages;
		messages.get(0).copy(this);
	}


	@Override
	public void setObject(Serializable newObject) throws JMSException {
		super.setObject(newObject);
	}

	@Override
	public Serializable getObject() throws JMSException {
		return new ArrayList<>(messages);
	}


}
