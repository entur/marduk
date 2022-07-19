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

package no.rutebanken.marduk.routes.chouette;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.rutebanken.marduk.domain.Provider;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http.HttpMethods;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.PROVIDER_IDS;


@Component
public class ChouetteStatsRouteBuilder extends AbstractChouetteRouteBuilder {

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Value("${chouette.stats.validity.categories}")
    private String[] validityCategories;

    @Value("${chouette.stats.days}")
    private int days;

    /**
     * Every ten minutes as default.
     */
    @Value("${chouette.stats.cache.refresh.quartz.trigger:trigger.repeatInterval=600000&trigger.repeatCount=-1&startDelayedSeconds=20&stateful=true}")
    private String quartzTrigger;

    private JsonNode cache;

    @Override
    public void configure() throws Exception {
        super.configure();


        // Fire only once at startup to initialize the cache.
        // Quartz job must run on all nodes.
        from("quartz://marduk/initialRefreshLine?trigger.repeatInterval=600000&trigger.repeatCount=-1&stateful=true")
                .process(this::setNewCorrelationId)
                .log(LoggingLevel.DEBUG, correlation() + "Quartz triggers initial refresh of line stats.")
                .to("direct:chouetteRefreshStatsCache")

                .log(LoggingLevel.DEBUG, correlation() + "Quartz initial refresh of line stats done.")
                .routeId("chouette-line-stats-cache-initial-refresh-quartz");

        // Periodically refresh the cache.
        // Quartz job must run on all nodes.
        from("quartz://marduk/refreshLine?" + quartzTrigger)
                .process(this::setNewCorrelationId)
                .log(LoggingLevel.DEBUG, correlation() + "Quartz triggers refresh of line stats.")
                .to("direct:chouetteRefreshStatsCache")

                .log(LoggingLevel.DEBUG, correlation() + "Quartz refresh of line stats done.")
                .routeId("chouette-line-stats-cache-refresh-quartz");


        from("direct:chouetteGetStatsSingleProvider")
                .choice().when(e -> cache == null)
                .to("direct:chouetteRejectGetStats")
                .end()
                .process(e -> e.getIn().setBody(cache.get(e.getIn().getHeader(PROVIDER_ID, String.class))))
                .choice().when(body().isNull())
                .log(LoggingLevel.WARN, correlation() + "No line statistics cached for provider: ${header." + PROVIDER_ID + "}")
                .end()
                .routeId("chouette-line-stats-get-single");

        from("direct:chouetteGetStats")
                .choice().when(e -> cache == null)
                .to("direct:chouetteRejectGetStats")
                .end()
                .process(this::populateWithMatchingLineStatsFromCache)
                .routeId("chouette-line-stats-get");

        // Rejecting calls to statistics while Marduk is starting up (i.e. the stats cache is not initialized yet).
        // This is safer than triggering the initialization of the cache
        // since this could happen multiple times in multiple threads in parallel and result in
        // uncontrolled load on the database.
        from("direct:chouetteRejectGetStats")
                .log(LoggingLevel.INFO, correlation() + "Rejecting call to get statistics while Marduk is starting up")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(503))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .setBody(constant("Statistics are temporarily unavailable"))
                .stop()
                .routeId("chouette-line-reject-stats-get");

        from("direct:chouetteGetFreshStats")
                .process(this::removeAllCamelHeaders)
                .setBody(constant(""))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .process(e -> e.getIn().setHeader("refParam", getAllReferentialsAsParam()))
                .setProperty("chouette_url", simple(chouetteUrl + "/chouette_iev/statistics/line?days=" + days + "&" + getValidityCategories() + "${header.refParam}"))
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "Calling chouette with ${exchangeProperty.chouette_url}")
                .doTry()
                .toD("${exchangeProperty.chouette_url}")
                .doCatch(HttpOperationFailedException.class).onWhen(exchange -> {
            HttpOperationFailedException ex = exchange.getException(HttpOperationFailedException.class);
            return (ex.getStatusCode() == 423);
        })
                .log(LoggingLevel.INFO, correlation() + "Unable to refresh statistics because Chouette is busy")
                .setBody(constant(""))
                .stop()
                .end()
                .log(LoggingLevel.INFO, correlation() + "Received refreshed statistics")
                .unmarshal().json(JsonLibrary.Jackson, Map.class)
                .process(e -> e.getIn().setBody(mapReferentialToProviderId(e.getIn().getBody(Map.class))))
                .marshal().json(JsonLibrary.Jackson)
                .routeId("chouette-line-stats-get-fresh");


        from("direct:chouetteRefreshStatsCache")
                .log(LoggingLevel.INFO, correlation() + "Refreshing line stats")
                .to("direct:chouetteGetFreshStats")
                .unmarshal().json(JsonLibrary.Jackson, JsonNode.class)
                .process(e -> cache = e.getIn().getBody(JsonNode.class))
                .setBody(constant(""))
                .log(LoggingLevel.INFO, correlation() + "Refresh of line stats done")
                .routeId("chouette-line-stats-cache-refresh");
    }


    private Map<Long, Object> mapReferentialToProviderId(Map<String, Object> statsPerReferential) {
        return getProviderRepository().getProviders().stream().filter(provider -> statsPerReferential.containsKey(provider.chouetteInfo.referential))
                .collect(Collectors.toMap(Provider::getId, provider -> statsPerReferential.get(provider.chouetteInfo.referential)));
    }

    private String getAllReferentialsAsParam() {
        List<String> referentials = getProviderRepository().getProviders().stream()
                .filter(provider -> provider.chouetteInfo != null && provider.chouetteInfo.referential != null)
                .map(provider -> provider.chouetteInfo.referential).collect(Collectors.toList());
        return "&referentials=" + String.join(",", referentials);
    }

    private List<Provider> getMatchingProviders(Exchange e) {
        List<String> providerIds = e.getIn().getHeader(PROVIDER_IDS, List.class);
        String filter = e.getIn().getHeader("filter", String.class);

        return getProviderRepository().getProviders().stream().filter(provider -> isMatch(provider, filter, providerIds)).collect(Collectors.toList());
    }

    private void populateWithMatchingLineStatsFromCache(Exchange e) {
        ObjectNode stats = JsonNodeFactory.instance.objectNode();
        getMatchingProviders(e).stream().map(provider -> provider.getId().toString()).forEach(providerId -> stats.set(providerId, cache.get(providerId)));
        e.getIn().setBody(stats);
    }


    boolean isMatch(Provider provider, String filter, List<String> whiteListedProviderIds) {
        boolean match = true;

        if (provider.chouetteInfo == null) {
            return false;
        }

        if (StringUtils.hasLength(filter) && !"all".equalsIgnoreCase(filter)) {
            if ("level1".equalsIgnoreCase(filter)) {
                match &= provider.getChouetteInfo().migrateDataToProvider != null;
            } else if ("level2".equalsIgnoreCase(filter)) {
                match &= provider.getChouetteInfo().migrateDataToProvider == null;
            } else {
                match = false;
            }
        }

        if (!CollectionUtils.isEmpty(whiteListedProviderIds)) {
            match &= whiteListedProviderIds.contains(provider.id.toString());
        }
        return match;
    }

    private String getValidityCategories() {
        return Arrays.stream(validityCategories).map(s -> "minDaysValidityCategory=" + s).collect(Collectors.joining("&"));
    }

}
