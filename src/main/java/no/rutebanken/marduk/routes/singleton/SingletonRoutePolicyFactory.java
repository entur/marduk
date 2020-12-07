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

package no.rutebanken.marduk.routes.singleton;


import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.component.hazelcast.policy.HazelcastRoutePolicy;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.rutebanken.hazelcasthelper.service.HazelCastService;
import org.rutebanken.hazelcasthelper.service.KubernetesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static no.rutebanken.marduk.Constants.SINGLETON_ROUTE_DEFINITION_GROUP_NAME;

/**
 * Create policies for enforcing that routes are started as singleton, using Hazelcast for cluster  sync.
 */
@Service
public class SingletonRoutePolicyFactory extends HazelCastService implements RoutePolicyFactory {

    private static final Logger log = LoggerFactory.getLogger(SingletonRoutePolicyFactory.class);

    @Value("${rutebanken.route.singleton.policy.ignore:false}")
    private boolean ignorePolicy;

    public SingletonRoutePolicyFactory(@Autowired KubernetesService kubernetesService) {
        super(kubernetesService);
    }

    /**
     * Create policy ensuring only one route with 'key' is started in cluster.
     */
    private RoutePolicy build(String key) {
        HazelcastRoutePolicy hazelcastRoutePolicy = new HazelcastRoutePolicy(this.hazelcast);
        hazelcastRoutePolicy.setLockMapName("lockMap");
        hazelcastRoutePolicy.setLockKey(key);
        hazelcastRoutePolicy.setLockValue("lockValueOther");
        hazelcastRoutePolicy.setShouldStopConsumer(true);

        return hazelcastRoutePolicy;
    }

    @Override
    public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, NamedNode node) {
        RouteDefinition routeDefinition = (RouteDefinition)node;
        try {
            if (!ignorePolicy && SINGLETON_ROUTE_DEFINITION_GROUP_NAME.equals(routeDefinition.getGroup())) {
                return build(routeId);
            }
        } catch (Exception e) {
            log.warn("Failed to create singleton route policy", e);
        }
        return null;
    }
}
