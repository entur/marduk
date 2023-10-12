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

package no.rutebanken.marduk.routes.otp.otp2;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.domain.OtpGraphsInfo;
import no.rutebanken.marduk.graph.OtpGraphFilesBuilder;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static no.rutebanken.marduk.Constants.*;

/**
 * List the latest street graphs and transit graphs present in storage for the 2 most recent serialization ids.
 */
@Component
public class Otp2ListGraphRouteBuilder extends BaseRouteBuilder {

    private static final String PROPERTY_OTP2_NETEX_GRAPH_FILES = "OTP2_NETEX_GRAPH_FILES";
    private final Pattern otp2NetexGraphFileNameRegex;
    private final Pattern otp2StreetGraphFileNameRegex;
    private final String blobStoreGraphSubdirectory;

    public Otp2ListGraphRouteBuilder(@Value("${otp.graph.blobstore.subdirectory:graphs}") String blobStoreGraphSubdirectory) {
        this.blobStoreGraphSubdirectory = blobStoreGraphSubdirectory;
        otp2NetexGraphFileNameRegex = Pattern.compile(OTP2_NETEX_GRAPH_DIR + "/" + "(.*)" + "/.*\\.obj");
        otp2StreetGraphFileNameRegex = Pattern.compile(blobStoreGraphSubdirectory + "/" + OTP2_STREET_GRAPH_DIR + "/" + OTP2_BASE_GRAPH_OBJ_PREFIX + "-(.*).*\\.obj");
    }

    @Override
    public void configure() throws Exception {
        super.configure();


        from("direct:listGraphs")
                // list the latest transit graphs (NeTEx graphs)
                .process(e -> e.getIn().setHeader(Constants.FILE_PARENT_COLLECTION, Set.of(OTP2_NETEX_GRAPH_DIR)))
                .to("direct:listOtpGraphBlobsInFolders")
                .process(exchange -> {
                    BlobStoreFiles blobStoreFiles = (BlobStoreFiles) exchange.getIn().getBody();
                    List<OtpGraphsInfo.OtpGraphFile> otpNetexGraphFiles = new OtpGraphFilesBuilder()
                            .withFileNameRegex(otp2NetexGraphFileNameRegex)
                            .withFiles(blobStoreFiles.getFiles())
                            .build();
                    exchange.setProperty(PROPERTY_OTP2_NETEX_GRAPH_FILES, otpNetexGraphFiles);

                })

                // list the latest street graphs (base graphs)
                .process(e -> e.getIn().setHeader(Constants.FILE_PARENT_COLLECTION, Set.of(blobStoreGraphSubdirectory + "/" + OTP2_STREET_GRAPH_DIR + "/")))
                .to("direct:listInternalBlobsInFolders")
                .process(exchange -> {
                    BlobStoreFiles blobStoreFiles = (BlobStoreFiles) exchange.getIn().getBody();
                    List<OtpGraphsInfo.OtpGraphFile> otpStreetGraphFiles = new OtpGraphFilesBuilder()
                            .withFileNameRegex(otp2StreetGraphFileNameRegex)
                            .withFiles(blobStoreFiles.getFiles())
                            .build();
                    List<OtpGraphsInfo.OtpGraphFile> otp2NetexGraphFiles = (List<OtpGraphsInfo.OtpGraphFile>) exchange.getProperty(PROPERTY_OTP2_NETEX_GRAPH_FILES);
                    exchange.getIn().setBody(new OtpGraphsInfo(otpStreetGraphFiles, otp2NetexGraphFiles));
                })
                .routeId("otp2-list-graphs");
    }

}
