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

package no.rutebanken.marduk.routes.gtfs;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.camel.Exchange.FILE_PARENT;

/**
 * Route creating a merged GTFS basic file for all providers and uploading it to GCS.
 * <p>
 * This file is adhering strictly to the GTFS specification, excluding all extended gtfs types.
 *
 * @see no.rutebanken.marduk.routes.gtfs.GtfsBasicMergedExportRouteBuilder for extended GTFS export
 */
@Component
public class GtfsBasicMergedExportRouteBuilder extends BaseRouteBuilder {

    @Value("${gtfs.basic.export.merged.file.name:rb_norway-aggregated-gtfs-basic.zip}")
    private String gtfsBasicMergedFileName;

    @Value("#{'${gtfs.basic.export.agency.prefix.blacklist:AVI}'.split(',')}")
    private Set<String> agencyBlackList;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("activemq:queue:GtfsBasicExportMergedQueue?transacted=true&maxConcurrentConsumers=1&messageListenerContainerFactoryRef=batchListenerContainerFactory").autoStartup("{{gtfs.export.basic.autoStartup:true}}")
                .transacted()
                .to("direct:exportGtfsBasicMerged")
                .inOnly("activemq:queue:GoogleExportQueue")
                .routeId("gtfs-basic-export-merged-jms-route");

        from("direct:exportGtfsBasicMerged")
                .setBody(constant(null))
                .setProperty(Constants.TRANSFORMATION_ROUTING_DESTINATION, constant("direct:transformToBasicGTFS"))
                .setProperty(Constants.PROVIDER_BLACK_LIST, constant(createProviderBlackList()))
                .setHeader(Constants.FILE_NAME, constant(gtfsBasicMergedFileName))
                .setHeader(Constants.JOB_ACTION, constant("EXPORT_GTFS_BASIC_MERGED"))
                .to("direct:exportMergedGtfs")
                .routeId("gtfs-basic-export-merged");

        from("direct:transformToBasicGTFS")
                .log(LoggingLevel.INFO, getClass().getName(), "Transforming gtfs to strict GTFS (no extensions)")
                .setBody(simple("${header." + FILE_PARENT + "}/merged.zip"))
                .bean("gtfsTransformationService", "transformToBasicGTFSFormat")
                .routeId("gtfs-basic-export-transform-gtfs");

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
