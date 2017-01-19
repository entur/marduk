package no.rutebanken.marduk.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.connection.JmsTransactionManager;

import javax.jms.ConnectionFactory;

@Configuration
public class TransactionManagerConfig {

    @Bean
    JmsTransactionManager transactionManager(@Autowired ConnectionFactory connectionFactory){
        return new JmsTransactionManager(connectionFactory);
    }

}
