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

package no.rutebanken.marduk.routes.flexlines;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;

import static no.rutebanken.marduk.Constants.BLOBSTORE_MAKE_BLOB_PUBLIC;
import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_CHOUETTE;
import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FOLDER_NAME;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

/**
 * Merge NeTEx dataset exported from Chouette with NeTEx dataset with flexible lines
 */
@Component
public class NetexFlexibleLinesExportRouteBuilder extends BaseRouteBuilder {

    private static final String UNPACKED_WITH_FLEXIBLE_LINES_SUB_FOLDER = "/unpacked-with-flexible-lines";
    private static final String MERGED_NETEX_SUB_FOLDER = "/result";

    @Value("${netex.export.download.directory:files/netex/merged}")
    private String localWorkingDirectory;

    @Value("${netex.export.merge.flexible.lines.enabled:false}")
    private String mergeFlexibleLinesEnabled;

    @Value("${gtfs.export.chouette:true}")
    private boolean useChouetteGtfsExport;

    private static final String EXPORT_FILE_NAME = "netex/${header." + CHOUETTE_REFERENTIAL + "}-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("google-pubsub:{{marduk.pubsub.project.id}}:FlexibleLinesExportQueue")
                .to("direct:mergeChouetteExportWithFlexibleLinesExport")
                .routeId("netex-export-merge-chouette-with-flexible-lines-queue");

        from("direct:mergeChouetteExportWithFlexibleLinesExport").streamCaching()
                .process(this::setCorrelationIdIfMissing)
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Merging chouette NeTEx export with FlexibleLines")
                .to("google-pubsub:{{marduk.pubsub.project.id}}:ChouetteMergeWithFlexibleLinesQueue")
                .routeId("netex-export-merge-chouette-with-flexible-lines");


    }
}
