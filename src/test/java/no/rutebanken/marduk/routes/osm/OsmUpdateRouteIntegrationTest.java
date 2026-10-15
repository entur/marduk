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

package no.rutebanken.marduk.routes.osm;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.exceptions.MardukException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OsmUpdateRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    private static final String CURRENT_MD5 = "471f160aa5580e7943acf1fb1c268d29";
    private static final String CURRENT_MD5_FILE_CONTENT = CURRENT_MD5 + "  norway-latest.osm.pbf\n";
    private static final String PREVIOUS_MD5_FILE_CONTENT = "0123456789abcdef0123456789abcdef  norway-latest.osm.pbf\n";

    @Value("${osm.pbf.blobstore.subdirectory:osm}")
    private String osmSubdirectory;

    @EndpointInject("mock:otp2BaseGraphBuildQueue")
    private MockEndpoint otp2BaseGraphBuildQueue;

    @Produce("direct:checkForOsmMapUpdate")
    private ProducerTemplate checkForOsmMapUpdate;

    @Test
    void testBaseGraphBuildIsTriggeredWhenNoChecksumIsStored() throws Exception {
        adviceRoutes();
        otp2BaseGraphBuildQueue.expectedMessageCount(1);

        context.start();
        checkForOsmMapUpdate.sendBodyAndHeader("", Constants.CORRELATION_ID, "test-correlation-id");

        otp2BaseGraphBuildQueue.assertIsSatisfied();
        assertEquals(CURRENT_MD5_FILE_CONTENT, storedMd5FileContent());

        // only the correlation id is published with the build request: no HTTP response header, no file handle
        assertEquals(Map.of(Constants.CORRELATION_ID, "test-correlation-id"), otp2BaseGraphBuildQueue.getExchanges().getFirst().getIn().getHeaders());
    }

    @Test
    void testBaseGraphBuildIsTriggeredWhenChecksumHasChanged() throws Exception {
        adviceRoutes();
        storeMd5FileContent(PREVIOUS_MD5_FILE_CONTENT);
        otp2BaseGraphBuildQueue.expectedMessageCount(1);

        context.start();
        checkForOsmMapUpdate.sendBody("");

        otp2BaseGraphBuildQueue.assertIsSatisfied();
        assertEquals(CURRENT_MD5_FILE_CONTENT, storedMd5FileContent());
    }

    @Test
    void testBaseGraphBuildIsNotTriggeredWhenChecksumIsUnchanged() throws Exception {
        adviceRoutes();
        storeMd5FileContent(CURRENT_MD5_FILE_CONTENT);
        otp2BaseGraphBuildQueue.expectedMessageCount(0);

        context.start();
        checkForOsmMapUpdate.sendBody("");

        otp2BaseGraphBuildQueue.assertIsSatisfied();
        assertEquals(CURRENT_MD5_FILE_CONTENT, storedMd5FileContent());
    }

    @Test
    void testChecksumIsNotStoredWhenTriggeringTheBuildFails() throws Exception {
        adviceRoutes();
        storeMd5FileContent(PREVIOUS_MD5_FILE_CONTENT);
        otp2BaseGraphBuildQueue.whenAnyExchangeReceived(e -> {
            throw new IllegalStateException("Simulated failure while publishing to the build queue");
        });

        context.start();
        assertThrows(CamelExecutionException.class, () -> checkForOsmMapUpdate.sendBody(""));

        // the stored checksum is left unchanged so that the build is triggered again on the next run
        assertEquals(PREVIOUS_MD5_FILE_CONTENT, storedMd5FileContent());
    }

    @Test
    void testBaseGraphBuildIsNotTriggeredWhenFetchedChecksumIsInvalid() throws Exception {
        adviceRoutes("");
        storeMd5FileContent(PREVIOUS_MD5_FILE_CONTENT);
        otp2BaseGraphBuildQueue.expectedMessageCount(0);

        context.start();
        CamelExecutionException exception = assertThrows(CamelExecutionException.class, () -> checkForOsmMapUpdate.sendBody(""));
        assertInstanceOf(MardukException.class, exception.getCause());

        otp2BaseGraphBuildQueue.assertIsSatisfied();
        assertEquals(PREVIOUS_MD5_FILE_CONTENT, storedMd5FileContent());
    }

    private void adviceRoutes() throws Exception {
        adviceRoutes(CURRENT_MD5_FILE_CONTENT);
    }

    private void adviceRoutes(String fetchedMd5FileContent) throws Exception {
        // replace the HTTP call to the osm-data bucket by a stub returning the given MD5 file content
        // together with a typical HTTP response header
        AdviceWith.adviceWith(context, "osm-fetch-md5sum", a -> a.weaveByToUri("https?://.*").replace().process(e -> {
            e.getMessage().setBody(fetchedMd5FileContent);
            e.getMessage().setHeader("ETag", "\"" + CURRENT_MD5 + "\"");
        }));
        AdviceWith.adviceWith(context, "osm-check-for-newer-map", a -> a.weaveByToUri("google-pubsub:(.*):Otp2BaseGraphBuildQueue").replace().to("mock:otp2BaseGraphBuildQueue"));
    }

    private String md5FileName() {
        return osmSubdirectory + "/norway-latest.osm.pbf.md5";
    }

    private void storeMd5FileContent(String content) {
        mardukInMemoryBlobStoreRepository.uploadBlob(md5FileName(), new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    private String storedMd5FileContent() throws IOException {
        InputStream blob = mardukInMemoryBlobStoreRepository.getBlob(md5FileName());
        if (blob == null) {
            return null;
        }
        return new String(blob.readAllBytes(), StandardCharsets.UTF_8);
    }
}
