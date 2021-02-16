package no.rutebanken.marduk.config;

import no.rutebanken.marduk.security.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
public class OAuth2Config {

    /**
     * Return a WebClient for authorized API calls.
     * The WebClient inserts a JWT bearer token in the Authorization HTTP header.
     * The JWT token is obtained from the configured Authorization Server.
     */
    @Bean
    WebClient webClient(@Autowired WebClient.Builder webClientBuilder, @Autowired TokenService tokenService) {

        ExchangeFilterFunction tokenRequestFilter = ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            ClientRequest.Builder builder = ClientRequest.from(clientRequest);
            builder.header("Authorization", "Bearer " + tokenService.getToken());
            return Mono.just(builder.build());
        });

        return webClientBuilder.filters(exchangeFilterFunctions -> exchangeFilterFunctions.add(tokenRequestFilter))
                .build();

    }


}
