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
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.camel.Exchange.FILE_PARENT;

/**
 * Route preparing and uploading GTFS export to google
 */
@Component
public class GoogleGtfsExportRoute extends BaseRouteBuilder {

    @Value("#{'${google.export.agency.prefix.blacklist:AVI}'.split(',')}")
    private Set<String> agencyBlackList;

    @Value("${google.export.file.name:google/google_norway-aggregated-gtfs.zip}")
    private String googleExportFileName;


    @Override
    public void configure() throws Exception {
        super.configure();


        singletonFrom("activemq:queue:GoogleExportQueue?transacted=true&maxConcurrentConsumers=1&messageListenerContainerFactoryRef=batchListenerContainerFactory").autoStartup("{{gtfs.export.basic.autoStartup:true}}")
                .transacted()
                .to("direct:exportGtfsGoogle")
                .routeId("gtfs-google-export-merged-jms-route");

        from("direct:transformToGoogleGTFS")
                .log(LoggingLevel.INFO, getClass().getName(), "Transforming gtfs to suit google")
                .setBody(simple("${header." + FILE_PARENT + "}/merged.zip"))
                .bean("gtfsTransformationService", "transformToGoogleFormat")
                .routeId("google-export-transform-gtfs");

        from("direct:exportGtfsGoogle")
                .setBody(constant(null))
                .setProperty(Constants.PROVIDER_BLACK_LIST, constant(createProviderBlackList()))
                .setProperty(Constants.TRANSFORMATION_ROUTING_DESTINATION, constant("direct:transformToGoogleGTFS"))
                .setHeader(Constants.FILE_NAME, constant(googleExportFileName))
                .setHeader(Constants.JOB_ACTION, constant("EXPORT_GOOGLE_GTFS"))
                .to("direct:exportMergedGtfs")
                .routeId("gtfs-google-export-merged");
    }


    /**
     * Make sure blacklisted agencies start with "rb_" prefix.
     */
    private List<String> createProviderBlackList() {
        if (agencyBlackList == null) {
            return new ArrayList<>();
        }

        return agencyBlackList.stream().map(agency -> agency.startsWith("rb_") ? agency : "rb_" + agency).collect(Collectors.toList());
    }


}
