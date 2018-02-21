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
import org.apache.commons.lang3.time.DateUtils;
import org.quartz.CronExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.text.ParseException;
import java.util.Date;
import java.util.List;

import static no.rutebanken.marduk.Constants.SINGLETON_ROUTE_DEFINITION_GROUP_NAME;


/**
 * Defines common route behavior.
 */
public abstract class BaseRouteBuilder extends SpringRouteBuilder {

    @Autowired
    private ProviderRepository providerRepository;

    @Value("${quartz.lenient.fire.time.ms:180000}")
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
     * Quartz should only trigger if singleton route is started, this node is the cluster leader for the route and fireTime is (almost) same as scheduledFireTime.
     * <p>
     * To avoid multiple firings in cluster and re-firing as route is resumed upon change of leadership.
     */
    protected boolean shouldQuartzRouteTrigger(Exchange e, String cron) {
        CronExpression cronExpression;
        String cleanCron = cron.replace("+", " ");
        try {
            cronExpression = new CronExpression(cleanCron);
        } catch (ParseException pe) {
            throw new RuntimeException("Invalid cron: " + cleanCron, pe);
        }
        return isStarted(e.getFromRouteId()) && isLeader(e.getFromRouteId()) && isScheduledQuartzFiring(e, cronExpression);
    }

    private boolean isScheduledQuartzFiring(Exchange exchange, CronExpression cron) {
        Date now = new Date();
        Date scheduledFireTime = cron.getNextValidTimeAfter(DateUtils.addMilliseconds(now, -lenientFireTimeMs));
        boolean isScheduledFiring = scheduledFireTime.before(now);

        if (!isScheduledFiring) {
            log.warn("Ignoring quartz trigger for route:{} as this is probably not a match for cron expression: {}", exchange.getFromRouteId(), cron.getCronExpression());
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
