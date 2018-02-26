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
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.LoggingLevel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.camel.Exchange.FILE_PARENT;

/**
 * Route preparing and uploading GTFS export to google.
 * <p>
 * Supports regular production export in addition to a separate dataset for testing / QA / onboarding new providers.
 */
@Component
public class GoogleGtfsExportRoute extends BaseRouteBuilder {

    @Value("#{'${google.export.agency.prefix.whitelist:}'.split(',')}")
    private Set<String> agencyWhiteList;

    @Value("${google.export.file.name:google/google_norway-aggregated-gtfs.zip}")
    private String googleExportFileName;

    @Value("#{'${google.export.qa.agency.prefix.whitelist:}'.split(',')}")
    private Set<String> qaAgencyWhiteList;

    @Value("${google.export.qa.file.name:google/google_norway-aggregated-qa-gtfs.zip}")
    private String googleQaExportFileName;


    @Override
    public void configure() throws Exception {
        super.configure();


        singletonFrom("activemq:queue:GoogleExportQueue?transacted=true&maxConcurrentConsumers=1&messageListenerContainerFactoryRef=batchListenerContainerFactory").autoStartup("{{google.export.autoStartup:true}}")
                .transacted()
                .to("direct:exportGtfsGoogle")
                .inOnly("activemq:queue:GoogleQaExportQueue")
                .routeId("gtfs-google-export-merged-jms-route");

        from("direct:transformToGoogleGTFS")
                .log(LoggingLevel.INFO, getClass().getName(), "Transforming gtfs to suit google")
                .setBody(simple("${header." + FILE_PARENT + "}/merged.zip"))
                .bean("gtfsTransformationService", "transformToGoogleFormat")
                .routeId("google-export-transform-gtfs");

        from("direct:exportGtfsGoogle")
                .setBody(constant(null))
                .setProperty(Constants.PROVIDER_WHITE_LIST, constant(prepareProviderWhiteList(agencyWhiteList)))
                .setProperty(Constants.TRANSFORMATION_ROUTING_DESTINATION, constant("direct:transformToGoogleGTFS"))
                .setHeader(Constants.FILE_NAME, constant(googleExportFileName))
                .setHeader(Constants.JOB_ACTION, constant("EXPORT_GOOGLE_GTFS"))
                .to("direct:exportMergedGtfs")
                .routeId("gtfs-google-export-merged");


        singletonFrom("activemq:queue:GoogleQaExportQueue?transacted=true&maxConcurrentConsumers=1&messageListenerContainerFactoryRef=batchListenerContainerFactory").autoStartup("{{google.export.qa.autoStartup:true}}")
                .transacted()
                .to("direct:exportQaGtfsGoogle")
                .routeId("gtfs-google-qa-export-merged-jms-route");


        from("direct:exportQaGtfsGoogle")
                .setBody(constant(null))
                .setProperty(Constants.PROVIDER_WHITE_LIST, constant(prepareProviderWhiteList(qaAgencyWhiteList)))
                .setProperty(Constants.TRANSFORMATION_ROUTING_DESTINATION, constant("direct:transformToGoogleGTFS"))
                .setHeader(Constants.FILE_NAME, constant(googleQaExportFileName))
                .setHeader(Constants.JOB_ACTION, constant("EXPORT_GOOGLE_GTFS_QA"))
                .to("direct:exportMergedGtfs")
                .routeId("gtfs-google-qa-export-merged");


    }


    /**
     * Make sure whitelisted agencies start with "rb_" prefix.
     */
    private List<String> prepareProviderWhiteList(Collection<String> rawIds) {
        if (rawIds == null) {
            return new ArrayList<>();
        }

        return rawIds.stream().filter(StringUtils::isNotEmpty).map(agency -> agency.startsWith("rb_") ? agency : "rb_" + agency).collect(Collectors.toList());
    }


}
