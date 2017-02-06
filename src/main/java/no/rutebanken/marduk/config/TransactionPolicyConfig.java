package no.rutebanken.marduk.config;


import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.connection.JmsTransactionManager;


@Configuration
public class TransactionPolicyConfig {

	@Autowired
	private JmsTransactionManager transactionManager;

	@Bean(name = "PROPAGATION_REQUIRES_NEW")
	public SpringTransactionPolicy propagationRequiresNewTransactionPolicy() {
		SpringTransactionPolicy transactionPolicy = new SpringTransactionPolicy(transactionManager);
		transactionPolicy.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
		return transactionPolicy;
	}
}
