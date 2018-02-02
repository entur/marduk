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

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.GtfsFileUtils;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.*;

/**
 * Route preparing and uploading GTFS export to google
 */
@Component
public class GoogleGtfsExportRoute extends BaseRouteBuilder {

    @Value("#{'${google.export.agency.prefix.blacklist:AVI}'.split(',')}")
    private Set<String> agencyBlackList;

    @Value("${google.export.download.directory:files/google}")
    private String localWorkingDirectory;

    @Value("${google.export.file.name:google_norway-aggregated-gtfs.zip}")
    private String googleExportFileName;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("activemq:queue:GoogleExportQueue?transacted=true&maxConcurrentConsumers=1&messageListenerContainerFactoryRef=batchListenerContainerFactory").autoStartup("{{google.export.autoStartup:true}}")
                .transacted()

                .log(LoggingLevel.INFO, getClass().getName(), "Start export of GTFS file for Google")

                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.TIMETABLE_PUBLISH).action("EXPORT_GOOGLE_GTFS").fileName(googleExportFileName).state(JobEvent.State.STARTED).newCorrelationId().build()).to("direct:updateStatus")

                .setHeader(Exchange.FILE_PARENT, constant(localWorkingDirectory))
                .to("direct:cleanUpLocalDirectory")

                .to("direct:fetchLatestGtfsForGoogleExport")
                .to("direct:mergeGtfsForGoogleExport")
                .to("direct:transformToGoogleGTFS")
                .to("direct:uploadGoogleGtfs")


                // Do not trigger publish on every build (done as scheduled job).to("activemq:queue:GooglePublishQueue")

                .to("direct:cleanUpLocalDirectory")

                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build()).to("direct:updateStatus")

                .log(LoggingLevel.INFO, getClass().getName(), "Completed export of GTFS file for Google")
                .routeId("google-export-route");


        from("direct:fetchLatestGtfsForGoogleExport")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching gtfs files for all non blacklisted providers.")
                .process(e -> e.getIn().setBody(getAggregatedGtfsFiles()))
                .split(body())
                .to("direct:getGtfsFileForGoogleExport")
                .routeId("google-export-fetch-latest-gtfs");

        from("direct:getGtfsFileForGoogleExport")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Fetching " + BLOBSTORE_PATH_OUTBOUND + "gtfs/${body}")
                .setProperty("fileName", body())
                .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + "gtfs/${property.fileName}"))

                .to("direct:getBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .toD("file:" + localWorkingDirectory + "?fileName=${property.fileName}")
                .otherwise()
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "${property.fileName} was empty when trying to fetch it from blobstore.")
                .routeId("google-export-get-latest-gtfs-for-provider");

        from("direct:mergeGtfsForGoogleExport")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Merging gtfs files for all providers.")
                .setBody(simple(localWorkingDirectory + "/"))
                .bean(method(GtfsFileUtils.class, "mergeGtfsFilesInDirectory"))
                .to("file:" + localWorkingDirectory + "?fileName=merged.zip")
                .routeId("google-export-merge-gtfs");

        from("direct:transformToGoogleGTFS")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Merging gtfs files for all providers.")
                .setBody(simple(localWorkingDirectory + "/merged.zip"))
                .bean("googleGtfsTransformationService")
                .routeId("google-export-transform-gtfs");

        from("direct:uploadGoogleGtfs")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Upload google formatted gtfs file.")
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(true))
                .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + "gtfs/google/" + googleExportFileName))
                .to("direct:uploadBlob")
                .routeId("google-export-upload-gtfs");

    }

    List<String> getAggregatedGtfsFiles() {
        return getProviderRepository().getProviders().stream()
                       .filter(p -> p.chouetteInfo.migrateDataToProvider == null)
                       .filter(p -> agencyBlackList.stream().noneMatch(blacklisted -> ("rb_" + blacklisted).equalsIgnoreCase(p.chouetteInfo.referential)))
                       .map(p -> p.chouetteInfo.referential + "-" + CURRENT_AGGREGATED_GTFS_FILENAME)
                       .collect(Collectors.toList());
    }


}
