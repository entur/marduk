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
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.GtfsFileUtils;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.BLOBSTORE_MAKE_BLOB_PUBLIC;
import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static no.rutebanken.marduk.Constants.CURRENT_AGGREGATED_GTFS_FILENAME;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.JOB_ACTION;
import static no.rutebanken.marduk.Constants.PROVIDER_BLACK_LIST;
import static no.rutebanken.marduk.Constants.PROVIDER_WHITE_LIST;
import static org.apache.camel.Exchange.FILE_PARENT;

/**
 * Common routes for building GTFS exports.
 */
@Component
public class CommonGtfsExportMergedRouteBuilder extends BaseRouteBuilder {


    @Value("${gtfs.export.download.directory:files/gtfs/merged}")
    private String localWorkingDirectory;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:exportMergedGtfs")
                .log(LoggingLevel.INFO, getClass().getName(), "Start export of merged GTFS file: ${header." + FILE_NAME + "}")

                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.TIMETABLE_PUBLISH).action(e.getIn().getHeader(JOB_ACTION, String.class)).state(JobEvent.State.STARTED).newCorrelationId().build())
                .inOnly("direct:updateStatus")
                .setHeader(FILE_PARENT, simple(localWorkingDirectory + "/${header." + JOB_ACTION + "}/${date:now:yyyyMMddHHmmssSSS}"))
                .doTry()
                .to("direct:fetchLatestGtfs")
                .to("direct:mergeGtfs")
                .to("direct:transformGtfs")
                .to("direct:uploadMergedGtfs")

                // Use wire tap to avoid replacing body
                .wireTap("direct:reportExportMergedGtfsOK")
                .end()
                .log(LoggingLevel.INFO, getClass().getName(), "Completed export of merged GTFS file: ${header." + FILE_NAME + "}")
                .doFinally()
                .to("direct:cleanUpLocalDirectory")
                .end()
                .routeId("gtfs-export-merged-route");


        from("direct:reportExportMergedGtfsOK")
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build())
                .inOnly("direct:updateStatus")
                .routeId("gtfs-export-merged-report-ok");

        from("direct:fetchLatestGtfs")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching gtfs files for all providers.")
                .process(e -> e.getIn().setBody(getAggregatedGtfsFiles(getProviderBlackList(e), getProviderWhiteList(e))))
                .choice().when(simple("${body.empty}"))
                .log(LoggingLevel.INFO, getClass().getName(), "No gtfs files configured for inclusion in export '${property.fileName}', terminating export.")
                .stop()
                .end()
                .split(body())
                .to("direct:getGtfsFiles")
                .routeId("gtfs-export-fetch-latest");

        from("direct:getGtfsFiles")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Fetching " + BLOBSTORE_PATH_OUTBOUND + "gtfs/${body}")
                .setProperty("fileName", body())
                .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + "gtfs/${property.fileName}"))
                .to("direct:getBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .toD("file:${header." + FILE_PARENT + "}/org?fileName=${property.fileName}")
                .otherwise()
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "${property.fileName} was empty when trying to fetch it from blobstore.")
                .routeId("gtfs-export-get-latest-for-provider");

        from("direct:mergeGtfs")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Merging gtfs files for all providers.")
                .setBody(simple("${header." + FILE_PARENT + "}/org"))
                .bean(method(GtfsFileUtils.class, "mergeGtfsFilesInDirectory"))
                .toD("file:${header." + FILE_PARENT + "}?fileName=merged.zip")

                .routeId("gtfs-export-merge");

        from("direct:transformGtfs")
                .choice().when(simple("${exchangeProperty." + Constants.TRANSFORMATION_ROUTING_DESTINATION + "} != null"))
                .toD("${exchangeProperty." + Constants.TRANSFORMATION_ROUTING_DESTINATION + "}")
                .routeId("gtfs-export-merged-transform");

        from("direct:uploadMergedGtfs")
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(true))
                .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + "gtfs/${header." + FILE_NAME + "}"))
                .to("direct:uploadBlob")
                .log(LoggingLevel.INFO, getClass().getName(), "Uploaded new merged GTFS file: ${header." + FILE_NAME + "}")
                .routeId("gtfs-export-upload-merged");


    }

    String getAggregatedGtfsFiles(Collection<String> providerBlackList, Collection<String> providerWhiteList) {
        String collect = getProviderRepository().getProviders().stream()
                                 .filter(p -> p.chouetteInfo.migrateDataToProvider == null)
                                 .filter(p -> isMatch(p, providerBlackList, providerWhiteList))
                                 .map(p -> p.chouetteInfo.referential + "-" + CURRENT_AGGREGATED_GTFS_FILENAME)
                                 .collect(Collectors.joining(","));
        return collect;
    }

    private boolean isMatch(Provider p, Collection<String> providerBlackList, Collection<String> providerWhiteList) {
        if (providerWhiteList == null) {
            return providerBlackList.stream().noneMatch(blacklisted -> blacklisted.equalsIgnoreCase(p.chouetteInfo.referential));
        }
        return providerWhiteList.stream().anyMatch(whiteListed -> whiteListed.equalsIgnoreCase(p.chouetteInfo.referential));
    }

    private Collection<String> getProviderBlackList(Exchange e) {
        Collection<String> providerBlackList = e.getProperty(PROVIDER_BLACK_LIST, Collection.class);
        if (providerBlackList == null) {
            providerBlackList = new ArrayList<>();
        }
        return providerBlackList;
    }

    private Collection<String> getProviderWhiteList(Exchange e) {
        return e.getProperty(PROVIDER_WHITE_LIST, Collection.class);
    }
}

