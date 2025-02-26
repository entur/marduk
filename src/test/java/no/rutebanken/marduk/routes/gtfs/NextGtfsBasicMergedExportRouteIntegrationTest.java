package no.rutebanken.marduk.routes.gtfs;

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.TestConstants;
import org.apache.camel.*;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static org.mockito.Mockito.when;

class NextGtfsBasicMergedExportRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {
    @Produce("direct:exportGtfsBasicMergedNext")
    protected ProducerTemplate startRoute;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @BeforeEach
    void prepare() {
        when(providerRepository.getProviders()).thenReturn(Arrays.asList(provider("rb_avi", 1, null), provider(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT, 2, null), provider("opp", 3, 4L)));
    }

    @Test
    void testUploadBasicGtfsMergedFile() throws Exception {
        mardukInMemoryBlobStoreRepository.uploadBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/rb_rut-aggregated-gtfs.zip", new FileInputStream(getExtendedGtfsTestFile()));
        mardukInMemoryBlobStoreRepository.uploadBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/rb_avi-aggregated-gtfs.zip", new FileInputStream(getExtendedGtfsTestFile()));

        AdviceWith
                .adviceWith(context, "gtfs-export-merged-route", a -> a.interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint()
                .to("mock:updateStatus"));

        updateStatus.expectedMessageCount(1);

        context.start();
        startRoute.requestBody(null);
        updateStatus.assertIsSatisfied();
    }

    private File getExtendedGtfsTestFile() throws IOException {
        Path extendedGTFSFile = Files.createTempFile("extendedGTFSFile", ".zip");
        Files.copy(Path.of( "src/test/resources/no/rutebanken/marduk/routes/gtfs/extended_gtfs.zip"), extendedGTFSFile, StandardCopyOption.REPLACE_EXISTING);
        return extendedGTFSFile.toFile();
    }
}
