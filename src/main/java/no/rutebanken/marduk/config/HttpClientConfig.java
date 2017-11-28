package no.rutebanken.marduk.config;

import org.apache.camel.CamelContext;
import org.apache.camel.component.http4.HttpClientConfigurer;
import org.apache.camel.component.http4.HttpComponent;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class HttpClientConfig {

    @Value("${http.client.name:marduk}")
    private String clientName;

    @Value("${HOSTNAME:marduk}")
    private String clientId;

    @Bean
    public HttpClientConfigurer httpClientConfigurer(@Autowired CamelContext camelContext) {
        HttpComponent httpComponent = camelContext.getComponent("http4", HttpComponent.class);
        HttpClientConfigurer httpClientConfigurer = new HttpClientConfigurer() {
            @Override
            public void configureHttpClient(HttpClientBuilder httpClientBuilder) {

                httpClientBuilder.setDefaultHeaders(Arrays.asList(new BasicHeader("CLIENT_ID", clientId), new BasicHeader("CLIENT_NAME", clientName)));
            }
        };

        httpComponent.setHttpClientConfigurer(httpClientConfigurer);
        return httpClientConfigurer;
    }

}
