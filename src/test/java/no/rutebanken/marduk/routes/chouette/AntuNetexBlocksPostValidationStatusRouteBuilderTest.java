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

package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.TestConstants;
import no.rutebanken.marduk.routes.netex.NetexDsjExportConfig;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.VALIDATION_CORRELATION_ID_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_DATASET_FILE_HANDLE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_EXPORT_NETEX_BLOCKS_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_HEADER;
import static no.rutebanken.marduk.routes.chouette.AntuNetexValidationStatusRouteBuilder.STATUS_VALIDATION_OK;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the EXPORT_NETEX_BLOCKS_POSTVALIDATION branch of
 * {@code direct:antuNetexValidationComplete}.
 * <p>
 * The branch copies the validated blocks export to its final path and then stores the legacy NeTEx 1.15
 * copy through {@code direct:distributeDsjNetexBlocksExport}. That distribution blanks the exchange body
 * in its {@code doFinally}, while the OK {@link JobEvent} had already been serialised into the body by the
 * preceding processor. Since {@code direct:updateStatus} publishes the body and nothing else (see
 * {@code StatusRouteBuilder}), Nabu received an empty message and never saw the OK event of the blocks
 * post-validation.
 * <p>
 * The filtered block is only reachable when post-validation is disabled, hence the dedicated property
 * source: {@code src/test/resources/application.properties} leaves {@code chouette.enablePostValidation}
 * at its {@code true} default, which is why this went unnoticed.
 */
@TestPropertySource(properties = {"chouette.enablePostValidation=false"})
class AntuNetexBlocksPostValidationStatusRouteBuilderTest extends MardukRouteBuilderIntegrationTestBase {

    private static final Path NETEX_1_16_FIXTURE =
            Path.of("src/test/resources/no/rutebanken/marduk/routes/netex/netex-1.16-dated-service-journey.xml");
    private static final String NETEX_1_16_ENTRY = "VYG_dated-service-journeys.xml";
    private static final String README_ENTRY = "README.txt";

    private static final String CORRELATION_ID_VALUE = "blocks-postvalidation-regression";

    @Produce("google-pubsub:{{marduk.pubsub.project.id}}:AntuNetexValidationStatusQueue")
    protected ProducerTemplate antuValidationStatusTemplate;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @Autowired
    private NetexDsjExportConfig netexDsjExportConfig;

    /**
     * Before the fix this does not fail on an assertion but on an empty body: {@code JobEvent.fromString("")}
     * throws a MardukException wrapping "No content to map due to end-of-input". The blank check below runs
     * first so that the failure names the actual defect.
     */
    @Test
    void blocksPostValidationOkPublishesTheJobEventAfterTheLegacyBlocksDistribution() throws Exception {
        String referential = TestConstants.CHOUETTE_REFERENTIAL_RB_RUT;
        // the file Antu validated, in the internal bucket; the branch copies it to blocksExportPath
        String validatedFileHandle = Constants.BLOBSTORE_PATH_NETEX_BLOCKS_EXPORT_BEFORE_VALIDATION
                + referential + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
        byte[] sourceArchive = zip(downgradableArchiveEntries());
        internalInMemoryBlobStoreRepository.uploadBlob(validatedFileHandle, new ByteArrayInputStream(sourceArchive));

        AdviceWith.adviceWith(context, "antu-netex-validation-complete", a -> a
                .interceptSendToEndpoint("direct:updateStatus")
                .skipSendToOriginalEndpoint()
                .to("mock:updateStatus"));

        // we must manually start when we are done with all the advice with
        context.start();

        updateStatus.expectedMessageCount(1);
        // the first XSLT downgrade in the JVM compiles the stylesheet
        updateStatus.setResultWaitTime(30_000);

        Map<String, String> headers = new HashMap<>();
        headers.put(VALIDATION_STAGE_HEADER, VALIDATION_STAGE_EXPORT_NETEX_BLOCKS_POSTVALIDATION);
        headers.put(VALIDATION_DATASET_FILE_HANDLE_HEADER, validatedFileHandle);
        headers.put(VALIDATION_CORRELATION_ID_HEADER, CORRELATION_ID_VALUE);
        headers.put(DATASET_REFERENTIAL, referential);
        sendBodyAndHeadersToPubSub(antuValidationStatusTemplate, STATUS_VALIDATION_OK, headers);

        updateStatus.assertIsSatisfied();

        // --- the regression itself -----------------------------------------------------------------
        String published = updateStatus.getExchanges().getFirst().getIn().getBody(String.class);
        assertThat(published)
                .as("direct:updateStatus publishes the body and nothing else: the serialised job event must "
                        + "survive the legacy blocks distribution, which blanks the body in its doFinally")
                .isNotBlank();

        JobEvent jobEvent = JobEvent.fromString(published);
        assertThat(jobEvent.getDomain()).isEqualTo(JobEvent.JobDomain.TIMETABLE);
        assertThat(jobEvent.getAction())
                .isEqualTo(JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS_POSTVALIDATION.name());
        assertThat(jobEvent.getState()).isEqualTo(JobEvent.State.OK);
        assertThat(jobEvent.getCorrelationId()).isEqualTo(CORRELATION_ID_VALUE);
        assertThat(jobEvent.getProviderId()).isEqualTo(TestConstants.PROVIDER_ID_RB_RUT);
        assertThat(jobEvent.getUsername())
                .as("the distribution defaults the username to the system when the export was not triggered "
                        + "by a user, and the event is now built after it")
                .isEqualTo("System");

        // --- proof that the filtered block really ran ----------------------------------------------
        // the job event is published on both sides of the filter, so without these the test would stay
        // green if chouette.enablePostValidation=false ever stopped reaching the route
        assertThat(internalBlob(netexDsjExportConfig.blocksExportPath(referential)))
                .as("the validated blocks export is copied to its final path")
                .isEqualTo(sourceArchive);

        byte[] legacyArchive = internalBlob(netexDsjExportConfig.legacyBlocksExportPath(referential));
        assertThat(legacyArchive)
                .as("direct:distributeDsjNetexBlocksExport produced the downgraded legacy copy, i.e. the route "
                        + "step that wipes the body did run")
                .isNotEqualTo(sourceArchive);
        String legacyNetex = new String(unzip(legacyArchive).get(NETEX_1_16_ENTRY), StandardCharsets.UTF_8);
        // the conversion also rewrites the Nordic profile version in the last segment
        assertThat(legacyNetex).contains("version=\"1.15:NO-NeTEx-networktimetable:1.5\"");
    }

    private byte[] internalBlob(String path) throws IOException {
        InputStream blob = internalInMemoryBlobStoreRepository.getBlob(path);
        assertThat(blob).as("Expected blob %s in the internal bucket", path).isNotNull();
        return blob.readAllBytes();
    }

    /** A zip with a NeTEx 1.16 file the XSLT can downgrade, plus a non-XML entry left untouched. */
    private static Map<String, byte[]> downgradableArchiveEntries() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(NETEX_1_16_ENTRY, Files.readAllBytes(NETEX_1_16_FIXTURE));
        entries.put(README_ENTRY, "not xml".getBytes(StandardCharsets.UTF_8));
        return entries;
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static Map<String, byte[]> unzip(byte[] archive) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                entries.put(entry.getName(), in.readAllBytes());
            }
        }
        return entries;
    }
}
