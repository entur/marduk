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

package no.rutebanken.marduk.routes;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.repository.ProviderRepository;
import org.apache.camel.Exchange;
import org.apache.camel.ServiceStatus;
import org.apache.camel.component.hazelcast.policy.HazelcastRoutePolicy;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spring.SpringRouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.Date;
import java.util.List;

import static no.rutebanken.marduk.Constants.SINGLETON_ROUTE_DEFINITION_GROUP_NAME;


/**
 * Defines common route behavior.
 */
public abstract class BaseRouteBuilder extends SpringRouteBuilder {

    @Autowired
    private ProviderRepository providerRepository;

    @Value("${quartz.lenient.fire.time.s:60000}")
    private int lenientFireTimeMs;

    @Override
    public void configure() throws Exception {
        errorHandler(transactionErrorHandler()
                             .logExhausted(true)
                             .logRetryStackTrace(true));
    }

    protected ProviderRepository getProviderRepository() {
        return providerRepository;
    }

    protected String correlation() {
        return "[providerId=${header." + Constants.PROVIDER_ID + "} referential=${header." + Constants.CHOUETTE_REFERENTIAL + "} correlationId=${header." + Constants.CORRELATION_ID + "}] ";
    }

    /**
     * Create a new singleton route definition from URI. Only one such route should be active throughout the cluster at any time.
     */
    protected RouteDefinition singletonFrom(String uri) {
        return this.from(uri).group(SINGLETON_ROUTE_DEFINITION_GROUP_NAME);
    }


    /**
     * Singleton route is only active if it is started and this node is the cluster leader for the route
     */
    protected boolean isSingletonRouteActive(String routeId) {
        return isStarted(routeId) && isLeader(routeId);
    }

    protected boolean isScheduledQuartzFiring(Exchange exchange) {
        Date scheduledFireTime = exchange.getIn().getHeader("scheduledFireTime", Date.class);
        Date fireTime = exchange.getIn().getHeader("fireTime", Date.class);
        if (fireTime == null || scheduledFireTime == null) {
            return false;
        }

        boolean isScheduledFiring = Math.abs(fireTime.getTime() - scheduledFireTime.getTime()) < lenientFireTimeMs;
        if (isScheduledFiring) {
            log.warn("Ignoring quartz trigger as fireTime ({}) is too far removed from scheduledFireTime ({})", fireTime, scheduledFireTime);
        }
        return isScheduledFiring;
    }

    protected boolean isStarted(String routeId) {
        ServiceStatus status = getContext().getRouteStatus(routeId);
        return status != null && status.isStarted();
    }

    protected boolean isLeader(String routeId) {
        RouteContext routeContext = getContext().getRoute(routeId).getRouteContext();
        List<RoutePolicy> routePolicyList = routeContext.getRoutePolicyList();
        if (routePolicyList != null) {
            for (RoutePolicy routePolicy : routePolicyList) {
                if (routePolicy instanceof HazelcastRoutePolicy) {
                    return ((HazelcastRoutePolicy) (routePolicy)).isLeader();
                }
            }
        }
        return false;
    }


}
