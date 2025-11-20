/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.config;

import jakarta.annotation.PostConstruct;
import no.rutebanken.marduk.rest.AuthorizationHeaderFilterStrategy;
import org.apache.camel.CamelContext;
import org.apache.camel.component.servlet.ServletComponent;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.springframework.context.annotation.Configuration;

/**
 * Ensures the Camel servlet component never propagates sensitive headers,
 * such as Authorization, back to HTTP clients.
 */
@Configuration
public class ServletComponentConfig {

    private final CamelContext camelContext;
    private final HeaderFilterStrategy authorizationHeaderFilterStrategy;

    public ServletComponentConfig(CamelContext camelContext,
                                  AuthorizationHeaderFilterStrategy authorizationHeaderFilterStrategy) {
        this.camelContext = camelContext;
        this.authorizationHeaderFilterStrategy = authorizationHeaderFilterStrategy;
    }

    @PostConstruct
    void registerHeaderFilterStrategy() {
        ServletComponent servletComponent = camelContext.getComponent("servlet", ServletComponent.class);
        servletComponent.setHeaderFilterStrategy(authorizationHeaderFilterStrategy);
    }
}
