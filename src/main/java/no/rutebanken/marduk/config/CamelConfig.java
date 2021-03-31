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

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.ThreadPoolBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;

@Configuration
public class CamelConfig {

    /**
     * Configure the Camel thread pool for bulk operations on providers.
     *
     * @param camelContext
     * @return
     * @throws Exception
     */
    @Bean
    public ExecutorService allProvidersExecutorService(@Autowired CamelContext camelContext) throws Exception {
        ThreadPoolBuilder poolBuilder = new ThreadPoolBuilder(camelContext);
        return poolBuilder
                .poolSize(20)
                .maxPoolSize(20)
                .maxQueueSize(1000)
                .build("allProvidersExecutorService");
    }

    /**
     * Configure the Camel thread pool for GTFS export routes.
     * The pool size is set to 1 in order to limit resource usage and prioritize other routes.
     * This means that at most one route among GTFS extended, GTFS basic, GTFS Google and GTFS Google QA export routes
     * can be running at any given time.
     *
     * @param camelContext
     * @return
     * @throws Exception
     */
    @Bean
    public ExecutorService gtfsExportExecutorService(@Autowired CamelContext camelContext) throws Exception {
        ThreadPoolBuilder poolBuilder = new ThreadPoolBuilder(camelContext);
        return poolBuilder
                .poolSize(1)
                .maxPoolSize(1)
                .maxQueueSize(100)
                .build("gtfsExportExecutorService");
    }

    /**
     * Register Java Time Module for JSON serialization/deserialization of Java Time objects.
     * @return
     */
    @Bean("jacksonJavaTimeModule")
    JavaTimeModule jacksonJavaTimeModule() {
        return new JavaTimeModule();
    }


}
