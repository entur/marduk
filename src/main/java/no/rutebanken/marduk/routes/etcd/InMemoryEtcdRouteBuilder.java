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

package no.rutebanken.marduk.routes.etcd;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * In memory impl of sync status routes. For testing without etcd.
 */
@Component
@ConditionalOnProperty(name = "etcd.in.memory", havingValue = "true")
public class InMemoryEtcdRouteBuilder extends BaseRouteBuilder {

    public Map<String, Object> values = new HashMap<>();


    @Override
    public void configure() throws Exception {
        from("direct:getEtcdValue")
                .process(e -> e.getIn().setBody(values.get(e.getIn().getHeader(Constants.ETCD_KEY, String.class))))
                .routeId("in-memory-etc-get-value");

        from("direct:setEtcdValue")
                .process(e -> values.put(e.getIn().getHeader(Constants.ETCD_KEY, String.class), e.getIn().getBody()))
                .routeId("in-memory-etc-set-value");
    }

    public void clean() {
        values = new HashMap<>();
    }
}
