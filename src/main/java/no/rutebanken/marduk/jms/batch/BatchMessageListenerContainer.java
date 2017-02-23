package no.rutebanken.marduk.jms.batch;

import org.apache.activemq.command.ActiveMQMessage;
import org.springframework.jms.connection.ConnectionFactoryUtils;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.listener.SessionAwareMessageListener;
import org.springframework.jms.support.JmsUtils;
import org.springframework.transaction.TransactionStatus;

import javax.jms.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Message listener container consuming up to a configurable no of messages, wrapping the batch as
 * content of an object message before invoking the message consumer.
 *
 * NB! supports only ActiveMQ
 */
public class BatchMessageListenerContainer extends DefaultMessageListenerContainer {
	public static final int DEFAULT_BATCH_SIZE = 100;

	private int batchSize;

	{
		batchSize = DEFAULT_BATCH_SIZE;
	}

	public BatchMessageListenerContainer() {
	}

	public BatchMessageListenerContainer(int batchSize) {
		this.batchSize = batchSize;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	/**
	 * The doReceiveAndExecute() method has to be overriden to support multiple-message receives.
	 */
	@Override
	protected boolean doReceiveAndExecute(Object invoker, Session session, MessageConsumer consumer,
			                                     TransactionStatus status) throws JMSException {
		Connection conToClose = null;
		MessageConsumer consumerToClose = null;
		Session sessionToClose = null;

		try {
			Session sessionToUse = session;
			MessageConsumer consumerToUse = consumer;

			if (sessionToUse == null) {
				Connection conToUse = null;
				if (sharedConnectionEnabled()) {
					conToUse = getSharedConnection();
				} else {
					conToUse = createConnection();
					conToClose = conToUse;
					conToUse.start();
				}
				sessionToUse = createSession(conToUse);
				sessionToClose = sessionToUse;
			}

			if (consumerToUse == null) {
				consumerToUse = createListenerConsumer(sessionToUse);
				consumerToClose = consumerToUse;
			}

			List<ActiveMQMessage> messages = new ArrayList<>();

			int count = 0;
			Message message = null;
			// Attempt to receive messages with the consumer
			do {
				message = receiveMessage(consumerToUse);
				if (message instanceof ActiveMQMessage) {
					messages.add((ActiveMQMessage) message);
				} else if (message != null) {
					throw new RuntimeException("Received message is not supported. Only ActiveMQMessages supported. Got: " + message.getClass());
				}
			}
			// Exit loop if no message was received in the time out specified, or
			// if the max batch size was met
			while ((message != null) && (++count < batchSize));

			if (messages.size() > 0) {
				// Only if messages were collected, notify the listener to consume the same.
				try {
					doExecuteListener(sessionToUse, messages);
					sessionToUse.commit();
				} catch (Throwable ex) {
					handleListenerException(ex);
					if (ex instanceof JMSException) {
						throw (JMSException) ex;
					}
				}
				return true;
			}

			// No message was received for the period of the timeout, return false.
			noMessageReceived(invoker, sessionToUse);
			return false;
		} finally {
			JmsUtils.closeMessageConsumer(consumerToClose);
			JmsUtils.closeSession(sessionToClose);
			ConnectionFactoryUtils.releaseConnection(conToClose, getConnectionFactory(), true);
		}
	}

	protected void doExecuteListener(Session session, List<ActiveMQMessage> messages) throws JMSException {
		if (!isAcceptMessagesWhileStopping() && !isRunning()) {
			if (logger.isWarnEnabled()) {
				logger.warn("Rejecting received messages because of the listener container "
						            + "having been stopped in the meantime: " + messages);
			}
			rollbackIfNecessary(session);
			throw new JMSException("Rejecting received messages as listener container is stopping");
		}


		JmsBatchMessage aggMsg = new JmsBatchMessage(messages);

		try {
			((SessionAwareMessageListener) getMessageListener()).onMessage(aggMsg, session);
		} catch (JMSException ex) {
			rollbackOnExceptionIfNecessary(session, ex);
			throw ex;
		} catch (RuntimeException ex) {
			rollbackOnExceptionIfNecessary(session, ex);
			throw ex;
		} catch (Error err) {
			rollbackOnExceptionIfNecessary(session, err);
			throw err;
		}
	}


	@Override
	protected void validateConfiguration() {
		if (batchSize <= 0) {
			throw new IllegalArgumentException("Property batchSize must be a value greater than 0");
		}
	}
}