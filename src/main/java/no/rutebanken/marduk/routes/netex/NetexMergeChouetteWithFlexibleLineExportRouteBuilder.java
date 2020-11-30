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

package no.rutebanken.marduk.routes.netex;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.BLOBSTORE_MAKE_BLOB_PUBLIC;
import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_CHOUETTE;
import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FOLDER_NAME;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

/**
 * Merge NeTEx dataset exported from Chouette with NeTEx dataset with flexible lines
 */
@Component
public class NetexMergeChouetteWithFlexibleLineExportRouteBuilder extends BaseRouteBuilder {

    private static final String UNPACKED_WITH_FLEXIBLE_LINES_SUB_FOLDER = "/unpacked-with-flexible-lines";
    private static final String MERGED_NETEX_SUB_FOLDER = "/result";

    @Value("${netex.export.download.directory:files/netex/merged}")
    private String localWorkingDirectory;

    @Value("${netex.export.merge.flexible.lines.enabled:false}")
    private String mergeFlexibleLinesEnabled;

    private static final String EXPORT_FILE_NAME = "netex/${header." + CHOUETTE_REFERENTIAL + "}-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("entur-google-pubsub:ChouetteMergeWithFlexibleLinesQueue")
                .to("direct:mergeChouetteExportWithFlexibleLinesExport")
                .routeId("netex-export-merge-chouette-with-flexible-lines-queue");

        from("direct:mergeChouetteExportWithFlexibleLinesExport").streamCaching()
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Merging chouette NeTEx export with FlexibleLines for provider ${header." + CHOUETTE_REFERENTIAL + "}")
                .validate(header(Constants.CHOUETTE_REFERENTIAL).isNotNull())

                .process(e -> e.getIn().setHeader(PROVIDER_ID, getProviderRepository().getProviderId(e.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class))))
                .validate(header(Constants.PROVIDER_ID).isNotNull())


                .setProperty(FOLDER_NAME, simple(localWorkingDirectory + "/${header." + CORRELATION_ID + "}_${date:now:yyyyMMddHHmmssSSS}"))
                .doTry()
                .to("direct:unpackChouetteExportToWorkingFolder")
                .to("direct:unpackFlexibleLinesExportToWorkingFolder")
                .to("direct:uploadWorkingFolderContent")
                .doFinally()
                .process(e -> e.getIn().setHeader(Exchange.FILE_PARENT, e.getProperty(FOLDER_NAME)))
                .to("direct:cleanUpLocalDirectory")
                .end()

                .setBody(constant(null))
                .process(e -> e.getIn().setHeader(Constants.CORRELATION_ID, e.getIn().getHeader(Constants.CORRELATION_ID, UUID.randomUUID().toString())))
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.BUILD_GRAPH).state(JobEvent.State.PENDING).build())
                .to("direct:updateStatus")

                .to("entur-google-pubsub:OtpGraphBuildQueue")

                .routeId("netex-export-merge-chouette-with-flexible-lines");


        from("direct:uploadWorkingFolderContent").streamCaching()
                .process(e -> new File(e.getProperty(FOLDER_NAME, String.class) + MERGED_NETEX_SUB_FOLDER).mkdir())
                .process(e -> e.getIn().setBody(ZipFileUtils.zipFilesInFolder(e.getProperty(FOLDER_NAME, String.class) + UNPACKED_WITH_FLEXIBLE_LINES_SUB_FOLDER, e.getProperty(FOLDER_NAME, String.class) + MERGED_NETEX_SUB_FOLDER + "/merged.zip")))


                .choice().when(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.generateDatedServiceJourneyIds)
                .to("direct:uploadDatedExport")
                .end()

                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, simple("true", Boolean.class))
                .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + EXPORT_FILE_NAME))
                .to("direct:uploadBlob")

                .routeId("netex-export-merge-chouette-with-flexbile-lines-working-folder");


        from("direct:unpackChouetteExportToWorkingFolder")
                .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_CHOUETTE + EXPORT_FILE_NAME))
                .to("direct:getBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .process(e -> ZipFileUtils.unzipFile(e.getIn().getBody(InputStream.class), e.getProperty(FOLDER_NAME, String.class) + UNPACKED_WITH_FLEXIBLE_LINES_SUB_FOLDER))
                .otherwise()
                .log(LoggingLevel.INFO, getClass().getName(), "${header." + FILE_HANDLE + "} was empty when trying to fetch it from blobstore.")
                .routeId("netex-export-merge-chouette-with-flexible-lines-unpack-chouette-export");


        from("direct:unpackFlexibleLinesExportToWorkingFolder")
                .choice().when(constant(mergeFlexibleLinesEnabled))
                .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + "netex/${header." + CHOUETTE_REFERENTIAL + "}-" + Constants.CURRENT_FLEXIBLE_LINES_NETEX_FILENAME))
                .to("direct:fetchExternalBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .process(e -> ZipFileUtils.unzipFile(e.getIn().getBody(InputStream.class), e.getProperty(FOLDER_NAME, String.class) + UNPACKED_WITH_FLEXIBLE_LINES_SUB_FOLDER))
                .otherwise()
                .log(LoggingLevel.INFO, getClass().getName(), "${header." + FILE_HANDLE + "} was empty when trying to fetch it from blobstore.")
                .otherwise()
                .log(LoggingLevel.INFO, getClass().getName(), "Skipping merge with flexible lines as this is diabled.")
                .end()
                .routeId("netex-export-merge-chouette-with-flexible-lines-unpack-flexible-lines-export");

    }
}
