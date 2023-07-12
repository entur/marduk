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

package no.rutebanken.marduk.routes.google;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.processor.aggregate.GroupedMessageAggregationStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Route preparing and uploading GTFS export to google.
 * <p>
 * Supports regular production export in addition to a separate dataset for testing / QA / onboarding new providers.
 */
@Component
public class GoogleGtfsExportRoute extends BaseRouteBuilder {

    @Value("${google.export.file.name:google/google_norway-aggregated-gtfs.zip}")
    private String googleExportFileName;

    @Value("${google.export.includes.shapes:false}")
    private boolean googleExportIncludeShapes;


    @Value("${google.export.qa.file.name:google/google_norway-aggregated-qa-gtfs.zip}")
    private String googleQaExportFileName;

    @Value("${google.export.qa.includes.shapes:false}")
    private String googleQaExportIncludeShapes;

    @Value("${google.export.aggregation.timeout:300000}")
    private int googleExportAggregationTimeout;

    @Override
    public void configure() throws Exception {
        super.configure();


        singletonFrom("google-pubsub:{{marduk.pubsub.project.id}}:GtfsGoogleExportQueue").autoStartup("{{google.export.autoStartup:true}}")
                .process(this::removeSynchronizationForAggregatedExchange)
                .aggregate(simple("true", Boolean.class)).aggregationStrategy(new GroupedMessageAggregationStrategy()).completionSize(100).completionTimeout(googleExportAggregationTimeout)
                .executorService("gtfsExportExecutorService")
                .process(this::addSynchronizationForAggregatedExchange)
                .process(this::setNewCorrelationId)
                .log(LoggingLevel.INFO, correlation() + "Aggregated ${exchangeProperty.CamelAggregatedSize} Google export requests (aggregation completion triggered by ${exchangeProperty.CamelAggregatedCompletedBy}).")
                .to("direct:exportGtfsGoogle")
                .to(ExchangePattern.InOnly, "google-pubsub:{{marduk.pubsub.project.id}}:GtfsGoogleQaExportQueue")
                .routeId("gtfs-google-export-merged-route");

        from("direct:exportGtfsGoogle")
                .setBody(constant(""))
                .process(e -> e.setProperty(Constants.PROVIDER_WHITE_LIST, prepareProviderWhiteListGoogleUpload()))
                .setHeader(Constants.FILE_NAME, constant(googleExportFileName))
                .setHeader(Constants.INCLUDE_SHAPES, constant(googleExportIncludeShapes))
                .setHeader(Constants.JOB_ACTION, constant("EXPORT_GOOGLE_GTFS"))
                .to("direct:exportMergedGtfs")
                .routeId("gtfs-google-export-merged");


        singletonFrom("google-pubsub:{{marduk.pubsub.project.id}}:GtfsGoogleQaExportQueue").autoStartup("{{google.export.qa.autoStartup:true}}")
                .process(this::removeSynchronizationForAggregatedExchange)
                .aggregate(simple("true", Boolean.class)).aggregationStrategy(new GroupedMessageAggregationStrategy()).completionSize(100).completionTimeout(googleExportAggregationTimeout)
                .executorService("gtfsExportExecutorService")
                .process(this::addSynchronizationForAggregatedExchange)
                .process(this::setNewCorrelationId)
                .log(LoggingLevel.INFO, correlation() +"Aggregated ${exchangeProperty.CamelAggregatedSize} Google QA export requests (aggregation completion triggered by ${exchangeProperty.CamelAggregatedCompletedBy}).")
                .to("direct:exportQaGtfsGoogle")
                .routeId("gtfs-google-qa-export-merged-route");


        from("direct:exportQaGtfsGoogle")
                .setBody(constant(""))
                .process(e -> e.setProperty(Constants.PROVIDER_WHITE_LIST, prepareProviderWhiteListGoogleQAUpload()))
                .setHeader(Constants.FILE_NAME, constant(googleQaExportFileName))
                .setHeader(Constants.INCLUDE_SHAPES, constant(googleQaExportIncludeShapes))
                .setHeader(Constants.JOB_ACTION, constant("EXPORT_GOOGLE_GTFS_QA"))
                .to("direct:exportMergedGtfs")
                .routeId("gtfs-google-qa-export-merged");


    }

    private Set<String> prepareProviderWhiteListGoogleUpload() {
        return getProviderRepository().getProviders().stream().filter(p -> p.getChouetteInfo().isGoogleUpload()).map(this::getExportReferentialForProvider).collect(Collectors.toSet());
    }

    private Set<String> prepareProviderWhiteListGoogleQAUpload() {
        return getProviderRepository().getProviders().stream().filter(p -> p.getChouetteInfo().isGoogleQAUpload()).map(this::getExportReferentialForProvider).collect(Collectors.toSet());
    }

    /**
     * Use referential for RB-space provider even if providers own space is configured for export.
     *
     * @param provider
     * @return
     */
    private String getExportReferentialForProvider(Provider provider) {
        if (provider.getChouetteInfo().getMigrateDataToProvider() != null) {

            Provider migrateToProvider = getProviderRepository().getProvider(provider.getChouetteInfo().getMigrateDataToProvider());
            if (migrateToProvider != null) {
                return migrateToProvider.getChouetteInfo().getReferential();
            }
        }
        return provider.getChouetteInfo().getReferential();
    }
}
