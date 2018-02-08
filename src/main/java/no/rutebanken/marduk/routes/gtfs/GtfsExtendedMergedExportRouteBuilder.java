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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Route creating a merged GTFS extended file for all providers and uploading it to GCS.
 * <p>
 * This file included fields and values proposed as GTFS extensions and as thus does not strictly adhere to the specification.
 *
 * @see no.rutebanken.marduk.routes.gtfs.GtfsBasicMergedExportRouteBuilder for strict GTFS export
 */
@Component
public class GtfsExtendedMergedExportRouteBuilder extends BaseRouteBuilder {


    @Value("${gtfs.norway.merged.file.name:rb_norway-aggregated-gtfs.zip}")
    private String gtfsNorwayMergedFileName;


    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("activemq:queue:GtfsExportMergedQueue?transacted=true&maxConcurrentConsumers=1&messageListenerContainerFactoryRef=batchListenerContainerFactory").autoStartup("{{gtfs.export.autoStartup:true}}")
                .transacted()
                .to("direct:exportGtfsExtendedMerged")
                .inOnly("activemq:queue:GtfsBasicExportMergedQueue")
                .routeId("gtfs-extended-export-merged-jms-route");


        from("direct:exportGtfsExtendedMerged")
                .setBody(constant(null))
                .setHeader(Constants.FILE_NAME, constant(gtfsNorwayMergedFileName))
                .setHeader(Constants.JOB_ACTION, constant("EXPORT_GTFS_MERGED"))
                .to("direct:exportMergedGtfs")
                .routeId("gtfs-extended-export-merged");

    }
}
