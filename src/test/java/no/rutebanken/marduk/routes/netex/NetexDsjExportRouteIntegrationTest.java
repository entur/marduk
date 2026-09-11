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
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.TestConstants;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.NETEX_1_16_ENTRY;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.OLD_NETEX_ENTRY;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.README_ENTRY;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.assertDowngraded;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.mixedArchiveEntries;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.unzip;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.zip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetexDsjExportRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @Produce("direct:distributeDsjNetexExport")
    protected ProducerTemplate distribute;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @Produce("direct:distributeDsjNetexBlocksExport")
    protected ProducerTemplate distributeBlocks;

    @Produce("direct:distributeOriginalDatasetToNisaba")
    protected ProducerTemplate distributeOriginal;

    @Produce("direct:copyOriginalDataset")
    protected ProducerTemplate copyOriginalDataset;

    @Produce("direct:distributeDsjNetexExportIfPublished")
    protected ProducerTemplate distributeIfPublished;

    @Autowired
    private NetexDsjExportConfig netexDsjExportConfig;

    @Value("${blobstore.gcs.exchange.container.name}")
    private String exchangeContainerName;

    @Value("${blobstore.gcs.nisaba.exchange.container.name}")
    private String nisabaExchangeContainerName;

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Autowired
    private Map<String, Map<String, byte[]>> blobsInContainers;

    private static final String LAST_UPDATE_DATE = "2026-09-11T12:03:04.397";

    private Map<String, byte[]> sourceEntries;
    private byte[] sourceArchive;

    @BeforeEach
    void prepare() throws Exception {
        AdviceWith.adviceWith(context, "netex-dsj-export-distribute", a -> a.interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint().to("mock:updateStatus"));
        // stub the call to Chouette that provides the timestamp of the dataset uploaded to Nisaba
        AdviceWith.adviceWith(context, "chouette-copy-original-dataset", a -> a.interceptSendToEndpoint(chouetteUrl + "/*")
                .skipSendToOriginalEndpoint()
                .setBody(a.constant(LAST_UPDATE_DATE)));
        sourceEntries = mixedArchiveEntries();
        sourceArchive = zip(sourceEntries);
        context.start();
    }

    @Test
    void distributeExportStoredInTheNewFolder() throws Exception {
        String referential = TestConstants.CHOUETTE_REFERENTIAL_RB_RUT;
        mardukInMemoryBlobStoreRepository.uploadBlob(netexDsjExportConfig.newExportPath(referential), new ByteArrayInputStream(sourceArchive));
        updateStatus.expectedMessageCount(2);

        distribute.requestBodyAndHeaders(null, distributeHeaders(TestConstants.PROVIDER_ID_RB_RUT, referential, "corr-1"));

        updateStatus.assertIsSatisfied();
        assertThat(jobStates()).containsExactly(JobEvent.State.STARTED, JobEvent.State.OK);
        assertThreeVariants(referential);
    }

    @Test
    void distributeSeedsTheNewFolderFromTheDefaultFolder() throws Exception {
        String referential = TestConstants.CHOUETTE_REFERENTIAL_RB_RUT;
        mardukInMemoryBlobStoreRepository.uploadBlob(netexDsjExportConfig.defaultExportPath(referential), new ByteArrayInputStream(sourceArchive));
        updateStatus.expectedMessageCount(2);

        distribute.requestBodyAndHeaders(null, distributeHeaders(TestConstants.PROVIDER_ID_RB_RUT, referential, "corr-2"));

        updateStatus.assertIsSatisfied();
        assertThreeVariants(referential);
    }

    @Test
    void distributeFailsWhenTheProviderHasNoExport() throws Exception {
        String referential = TestConstants.CHOUETTE_REFERENTIAL_RB_RUT;
        updateStatus.expectedMessageCount(2);
        Map<String, Object> headers = distributeHeaders(TestConstants.PROVIDER_ID_RB_RUT, referential, "corr-3");

        assertThatThrownBy(() -> distribute.requestBodyAndHeaders(null, headers))
                .isInstanceOf(CamelExecutionException.class)
                .hasRootCauseMessage("No NeTEx export found for the provider");

        updateStatus.assertIsSatisfied();
        assertThat(jobStates()).containsExactly(JobEvent.State.STARTED, JobEvent.State.FAILED);
        assertThat(mardukInMemoryBlobStoreRepository.getBlob(netexDsjExportConfig.legacyExportPath(referential))).isNull();
        assertThat(mardukInMemoryBlobStoreRepository.getBlob(netexDsjExportConfig.defaultExportPath(referential))).isNull();
    }

    @Test
    void distributeBlocksExportStoresALegacyCopy() throws Exception {
        String referential = TestConstants.CHOUETTE_REFERENTIAL_RB_RUT;
        internalInMemoryBlobStoreRepository.uploadBlob(netexDsjExportConfig.blocksExportPath(referential), new ByteArrayInputStream(sourceArchive));

        Map<String, Object> headers = new java.util.HashMap<>(distributeHeaders(TestConstants.PROVIDER_ID_RB_RUT, referential, "corr-4"));
        headers.put(Constants.FILE_HANDLE, "some/validated/file.zip");
        Exchange result = distributeBlocks.send(distributeBlocks.getDefaultEndpoint(), e -> e.getIn().setHeaders(headers));

        assertThat(result.getException()).isNull();
        assertThat(result.getIn().getHeader(Constants.FILE_HANDLE)).as("FILE_HANDLE is restored for the caller").isEqualTo("some/validated/file.zip");
        // the export produced by the pipeline is untouched
        assertThat(internalBlob(netexDsjExportConfig.blocksExportPath(referential))).isEqualTo(sourceArchive);
        Map<String, byte[]> legacyEntries = unzip(internalBlob(netexDsjExportConfig.legacyBlocksExportPath(referential)));
        assertThat(legacyEntries.keySet()).containsExactly(NETEX_1_16_ENTRY, OLD_NETEX_ENTRY, README_ENTRY);
        assertDowngraded(new String(legacyEntries.get(NETEX_1_16_ENTRY), StandardCharsets.UTF_8));
        assertThat(legacyEntries.get(OLD_NETEX_ENTRY)).isEqualTo(sourceEntries.get(OLD_NETEX_ENTRY));
    }

    /**
     * The legacy folder and the default folder are written by the route itself; the caller stores the dataset as
     * uploaded in the new folder, so the headers it needs are restored.
     */
    @Test
    void distributeOriginalDatasetToNisaba() throws Exception {
        String referential = TestConstants.CHOUETTE_REFERENTIAL_RB_RUT;
        String originalFileHandle = "inbound/received/rb_rut/original.zip";
        String nisabaFileHandle = "imported/rb_rut/rb_rut_2026-09-10T10_00_00.000.zip";
        internalInMemoryBlobStoreRepository.uploadBlob(originalFileHandle, new ByteArrayInputStream(sourceArchive));

        Map<String, Object> headers = new java.util.HashMap<>(distributeHeaders(TestConstants.PROVIDER_ID_RB_RUT, referential, "corr-5"));
        headers.put(Constants.FILE_HANDLE, originalFileHandle);
        headers.put(Constants.TARGET_FILE_HANDLE, nisabaFileHandle);
        // the exchange bucket stands in for the Nisaba bucket
        headers.put(Constants.TARGET_CONTAINER, exchangeContainerName);
        Exchange result = distributeOriginal.send(distributeOriginal.getDefaultEndpoint(), e -> e.getIn().setHeaders(headers));

        assertThat(result.getException()).isNull();
        assertThat(result.getIn().getHeader(Constants.FILE_HANDLE)).isEqualTo(originalFileHandle);
        assertThat(result.getIn().getHeader(Constants.TARGET_FILE_HANDLE)).isEqualTo(nisabaFileHandle);
        assertThat(netexDsjExportConfig.originalDatasetPublicationPath(nisabaFileHandle))
                .isEqualTo("imported-dsj-new/rb_rut/rb_rut_2026-09-10T10_00_00.000.zip");

        byte[] legacyBlob = exchangeBlob("imported-dsj-legacy/rb_rut/rb_rut_2026-09-10T10_00_00.000.zip");
        Map<String, byte[]> legacyEntries = unzip(legacyBlob);
        assertDowngraded(new String(legacyEntries.get(NETEX_1_16_ENTRY), StandardCharsets.UTF_8));
        assertThat(legacyEntries.get(OLD_NETEX_ENTRY)).isEqualTo(sourceEntries.get(OLD_NETEX_ENTRY));

        // the default folder receives the legacy variant (netex.export.dsj.default.variant=legacy in the test configuration)
        assertThat(netexDsjExportConfig.getDefaultVariant()).isEqualTo(NetexDsjExportConfig.Variant.LEGACY);
        assertThat(exchangeBlob(nisabaFileHandle)).isEqualTo(legacyBlob);
    }

    /**
     * Datasets of codespaces without DatedServiceJourney replacement information are not downgraded: the legacy
     * variants are plain copies.
     */
    @Test
    void codespacesWithoutReplacementInformationAreCopiedInsteadOfDowngraded() throws Exception {
        assertThat(netexDsjExportConfig.hasDsjReplacements("rb_vyg")).isTrue();
        assertThat(netexDsjExportConfig.hasDsjReplacements("VYG")).isTrue();
        assertThat(netexDsjExportConfig.hasDsjReplacements("rb_atb")).isFalse();

        String referential = "rb_atb";
        mardukInMemoryBlobStoreRepository.uploadBlob(netexDsjExportConfig.newExportPath(referential), new ByteArrayInputStream(sourceArchive));
        internalInMemoryBlobStoreRepository.uploadBlob(netexDsjExportConfig.blocksExportPath(referential), new ByteArrayInputStream(sourceArchive));
        updateStatus.expectedMessageCount(2);

        distribute.requestBodyAndHeaders(null, distributeHeaders(TestConstants.PROVIDER_ID_RB_RUT, referential, "corr-6"));
        Map<String, Object> blocksHeaders = new java.util.HashMap<>(distributeHeaders(TestConstants.PROVIDER_ID_RB_RUT, referential, "corr-6"));
        Exchange blocksResult = distributeBlocks.send(distributeBlocks.getDefaultEndpoint(), e -> e.getIn().setHeaders(blocksHeaders));

        updateStatus.assertIsSatisfied();
        assertThat(blocksResult.getException()).isNull();
        assertThat(blob(netexDsjExportConfig.legacyExportPath(referential))).isEqualTo(sourceArchive);
        assertThat(blob(netexDsjExportConfig.defaultExportPath(referential))).isEqualTo(sourceArchive);
        assertThat(internalBlob(netexDsjExportConfig.legacyBlocksExportPath(referential))).isEqualTo(sourceArchive);
    }

    /**
     * The original dataset is uploaded to Nisaba by the import pipeline (direct:copyOriginalDataset), which stores it
     * in the three folders used during the transition.
     */
    @Test
    void copyOriginalDatasetStoresTheThreeVariantsInNisaba() throws Exception {
        String referential = TestConstants.CHOUETTE_REFERENTIAL_RUT;
        String originalFileHandle = "inbound/received/rut/original.zip";
        internalInMemoryBlobStoreRepository.uploadBlob(originalFileHandle, new ByteArrayInputStream(sourceArchive));

        Map<String, Object> headers = new java.util.HashMap<>(distributeHeaders(TestConstants.PROVIDER_ID_RUT, referential, "corr-7"));
        headers.put(Constants.FILE_HANDLE, originalFileHandle);
        Exchange result = copyOriginalDataset.send(copyOriginalDataset.getDefaultEndpoint(), e -> e.getIn().setHeaders(headers));

        assertThat(result.getException()).isNull();
        Map<String, byte[]> nisabaBlobs = blobsInContainers.get(nisabaExchangeContainerName);
        String fileName = "rut/rut_" + LAST_UPDATE_DATE.replace(":", "_") + ".zip";
        assertThat(nisabaBlobs).as("Expected the three variants of the original dataset in the Nisaba bucket")
                .containsKeys("imported/" + fileName, "imported-dsj-legacy/" + fileName, "imported-dsj-new/" + fileName);

        // the new folder holds the dataset as uploaded
        assertThat(nisabaBlobs.get("imported-dsj-new/" + fileName)).isEqualTo(sourceArchive);

        // the legacy folder holds the downgraded copy, and the default folder the default variant (legacy)
        Map<String, byte[]> legacyEntries = unzip(nisabaBlobs.get("imported-dsj-legacy/" + fileName));
        assertDowngraded(new String(legacyEntries.get(NETEX_1_16_ENTRY), StandardCharsets.UTF_8));
        assertThat(legacyEntries.get(OLD_NETEX_ENTRY)).isEqualTo(sourceEntries.get(OLD_NETEX_ENTRY));
        assertThat(nisabaBlobs.get("imported/" + fileName)).isEqualTo(nisabaBlobs.get("imported-dsj-legacy/" + fileName));
    }

    private byte[] exchangeBlob(String path) throws IOException {
        InputStream blob = exchangeInMemoryBlobStoreRepository.getBlob(path);
        assertThat(blob).as("Expected blob %s in the Nisaba bucket", path).isNotNull();
        return blob.readAllBytes();
    }

    /**
     * A provider that has never published has no export in any folder: the backfill skips it instead of reporting a
     * failed export.
     */
    @Test
    void providersWithoutAnyExportAreSkippedByTheBackfill() throws Exception {
        String referential = TestConstants.CHOUETTE_REFERENTIAL_RB_RUT;
        updateStatus.expectedMessageCount(0);

        Exchange result = distributeIfPublished.send(distributeIfPublished.getDefaultEndpoint(),
                e -> e.getIn().setHeaders(distributeHeaders(TestConstants.PROVIDER_ID_RB_RUT, referential, "corr-8")));

        assertThat(result.getException()).isNull();
        updateStatus.assertIsSatisfied();
        assertThat(mardukInMemoryBlobStoreRepository.getBlob(netexDsjExportConfig.legacyExportPath(referential))).isNull();
    }

    /**
     * A provider that has not published since the dual export was enabled still has its export in the default folder:
     * the backfill distributes it from there.
     */
    @Test
    void providersWithAnExportInTheDefaultFolderAreDistributedByTheBackfill() throws Exception {
        String referential = TestConstants.CHOUETTE_REFERENTIAL_RB_RUT;
        mardukInMemoryBlobStoreRepository.uploadBlob(netexDsjExportConfig.defaultExportPath(referential), new ByteArrayInputStream(sourceArchive));
        updateStatus.expectedMessageCount(2);

        Exchange result = distributeIfPublished.send(distributeIfPublished.getDefaultEndpoint(),
                e -> e.getIn().setHeaders(distributeHeaders(TestConstants.PROVIDER_ID_RB_RUT, referential, "corr-9")));

        assertThat(result.getException()).isNull();
        updateStatus.assertIsSatisfied();
        assertThat(jobStates()).containsExactly(JobEvent.State.STARTED, JobEvent.State.OK);
        assertThreeVariants(referential);
    }

    private byte[] internalBlob(String path) throws IOException {
        InputStream blob = internalInMemoryBlobStoreRepository.getBlob(path);
        assertThat(blob).as("Expected internal blob %s", path).isNotNull();
        return blob.readAllBytes();
    }

    private void assertThreeVariants(String referential) throws IOException {
        // the new variant is the export as published
        assertThat(blob(netexDsjExportConfig.newExportPath(referential))).isEqualTo(sourceArchive);

        // the legacy variant contains the downgraded NeTEx 1.16 file and the other files unchanged
        byte[] legacyArchive = blob(netexDsjExportConfig.legacyExportPath(referential));
        Map<String, byte[]> legacyEntries = unzip(legacyArchive);
        assertThat(legacyEntries.keySet()).containsExactly(NETEX_1_16_ENTRY, OLD_NETEX_ENTRY, README_ENTRY);
        assertDowngraded(new String(legacyEntries.get(NETEX_1_16_ENTRY), StandardCharsets.UTF_8));
        assertThat(legacyEntries.get(OLD_NETEX_ENTRY)).isEqualTo(sourceEntries.get(OLD_NETEX_ENTRY));
        assertThat(legacyEntries.get(README_ENTRY)).isEqualTo(sourceEntries.get(README_ENTRY));

        // the default folder receives the legacy variant (netex.export.dsj.default.variant=legacy in the test configuration)
        assertThat(netexDsjExportConfig.getDefaultVariant()).isEqualTo(NetexDsjExportConfig.Variant.LEGACY);
        assertThat(blob(netexDsjExportConfig.defaultExportPath(referential))).isEqualTo(legacyArchive);
    }

    private byte[] blob(String path) throws IOException {
        InputStream blob = mardukInMemoryBlobStoreRepository.getBlob(path);
        assertThat(blob).as("Expected blob %s", path).isNotNull();
        return blob.readAllBytes();
    }

    private List<JobEvent.State> jobStates() {
        return updateStatus.getExchanges().stream()
                .map(e -> JobEvent.fromString(e.getIn().getBody(String.class)).getState())
                .toList();
    }

    private static Map<String, Object> distributeHeaders(Long providerId, String referential, String correlationId) {
        return Map.of(Constants.PROVIDER_ID, providerId, Constants.CHOUETTE_REFERENTIAL, referential, Constants.CORRELATION_ID, correlationId);
    }
}
