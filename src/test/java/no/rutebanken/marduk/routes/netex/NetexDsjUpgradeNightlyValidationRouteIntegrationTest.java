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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_NIGHTLY_VALIDATION;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.NETEX_1_15_FIXTURE;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.NETEX_1_16_ENTRY;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.OLD_NETEX_ENTRY;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.README_ENTRY;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.assertUpgraded;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.mixedArchiveEntries;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.unzip;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.zip;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The nightly validation revalidates the dataset stored by the last upload, which may predate the switch to
 * NeTEx 1.16. It must be upgraded before it is sent to Antu, or the pipeline validates and re-imports a dataset
 * whose DatedServiceJourney replacement references a NeTEx 1.16 Chouette drops silently.
 */
class NetexDsjUpgradeNightlyValidationRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    private static final String NETEX_1_15_ENTRY = "VYG_netex-1.15.xml";
    private static final String FILE_HANDLE = "inbound/received/rb_rut/netex.zip";

    @Produce("direct:antuNetexNightlyValidation")
    protected ProducerTemplate nightlyValidation;

    @EndpointInject("mock:antuValidationQueue")
    protected MockEndpoint antuValidationQueue;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @Autowired
    private Map<String, Map<String, byte[]>> blobsInContainers;

    @Value("${blobstore.gcs.antu.exchange.container.name}")
    private String antuExchangeContainerName;

    private byte[] datasetWithFilesToUpgrade;
    private byte[] datasetWithoutFilesToUpgrade;

    @BeforeEach
    void prepare() throws Exception {
        AdviceWith.adviceWith(context, "antu-netex-nightly-validation", a -> {
            a.weaveByToUri("google-pubsub:(.*):AntuNetexValidationQueue").replace().to("mock:antuValidationQueue");
            a.interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint().to("mock:updateStatus");
        });
        AdviceWith.adviceWith(context, "antu-netex-nightly-validation-upgrade",
                a -> a.interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint().to("mock:updateStatus"));

        // mixedArchiveEntries() holds a NeTEx 1.16 file, a NeTEx file declaring an older version and a non-XML file
        Map<String, byte[]> entries = new LinkedHashMap<>(mixedArchiveEntries());
        Map<String, byte[]> upToDateEntries = new LinkedHashMap<>(entries);
        upToDateEntries.remove(OLD_NETEX_ENTRY);
        datasetWithoutFilesToUpgrade = zip(upToDateEntries);
        entries.put(NETEX_1_15_ENTRY, Files.readAllBytes(NETEX_1_15_FIXTURE));
        datasetWithFilesToUpgrade = zip(entries);
        blobsInContainers.remove(antuExchangeContainerName);
        context.start();
    }

    /**
     * The dataset must be upgraded before it is copied to the validation bucket: what Antu and, after it, Chouette
     * read is the copy in that bucket, not the stored file.
     */
    @Test
    void theDatasetSentToAntuIsUpgradedToNetex116() throws Exception {
        internalInMemoryBlobStoreRepository.uploadBlob(FILE_HANDLE, new ByteArrayInputStream(datasetWithFilesToUpgrade));
        antuValidationQueue.expectedMessageCount(1);

        Exchange result = nightlyValidation.send(nightlyValidation.getDefaultEndpoint(),
                e -> e.getIn().setHeaders(nightlyHeaders(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT)));

        assertThat(result.getException()).isNull();
        antuValidationQueue.assertIsSatisfied();

        Map<String, byte[]> sentToAntu = unzip(validationBucketBlob());
        assertThat(sentToAntu.keySet()).containsExactly(NETEX_1_16_ENTRY, OLD_NETEX_ENTRY, README_ENTRY, NETEX_1_15_ENTRY);
        assertUpgraded(new String(sentToAntu.get(NETEX_1_15_ENTRY), StandardCharsets.UTF_8));
        assertThat(new String(sentToAntu.get(OLD_NETEX_ENTRY), StandardCharsets.UTF_8)).contains("version=\"1.16\"");

        // the stored dataset is upgraded in place, so the next nightly run has nothing left to convert
        assertThat(internalBlob(FILE_HANDLE)).isEqualTo(validationBucketBlob());

        Exchange sent = antuValidationQueue.getExchanges().getFirst();
        assertThat(sent.getIn().getHeader(VALIDATION_STAGE_HEADER)).isEqualTo(VALIDATION_STAGE_NIGHTLY_VALIDATION);
        assertThat(sent.getIn().getHeader(Constants.VALIDATION_DATASET_FILE_HANDLE_HEADER)).isEqualTo(FILE_HANDLE);
    }

    @Test
    void aDatasetAlreadyInNetex116IsSentAsIs() throws Exception {
        internalInMemoryBlobStoreRepository.uploadBlob(FILE_HANDLE, new ByteArrayInputStream(datasetWithoutFilesToUpgrade));
        antuValidationQueue.expectedMessageCount(1);

        Exchange result = nightlyValidation.send(nightlyValidation.getDefaultEndpoint(),
                e -> e.getIn().setHeaders(nightlyHeaders(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT)));

        assertThat(result.getException()).isNull();
        antuValidationQueue.assertIsSatisfied();
        assertThat(validationBucketBlob()).isEqualTo(datasetWithoutFilesToUpgrade);
        assertThat(internalBlob(FILE_HANDLE)).isEqualTo(datasetWithoutFilesToUpgrade);
        // nothing was converted, so the stored dataset was not rewritten: direct:uploadInternalBlob sets FILE_VERSION
        assertThat(antuValidationQueue.getExchanges().getFirst().getIn().getHeader(Constants.FILE_VERSION)).isNull();
    }

    @Test
    void theDatasetOfAnotherCodespaceIsSentAsIs() throws Exception {
        internalInMemoryBlobStoreRepository.uploadBlob(FILE_HANDLE, new ByteArrayInputStream(datasetWithFilesToUpgrade));
        antuValidationQueue.expectedMessageCount(1);

        Exchange result = nightlyValidation.send(nightlyValidation.getDefaultEndpoint(), e -> e.getIn().setHeaders(nightlyHeaders("rb_atb")));

        assertThat(result.getException()).isNull();
        antuValidationQueue.assertIsSatisfied();
        assertThat(validationBucketBlob()).isEqualTo(datasetWithFilesToUpgrade);
        assertThat(internalBlob(FILE_HANDLE)).isEqualTo(datasetWithFilesToUpgrade);
    }

    /**
     * A dataset that cannot be upgraded must not be validated as it is: the nightly run of that provider fails and
     * reports a failed pre-validation instead.
     */
    @Test
    void aFailedUpgradeFailsTheNightlyRunAndSendsNothingToAntu() throws Exception {
        internalInMemoryBlobStoreRepository.uploadBlob(FILE_HANDLE,
                new ByteArrayInputStream("not a zip archive".getBytes(StandardCharsets.UTF_8)));
        antuValidationQueue.expectedMessageCount(0);
        updateStatus.expectedMessageCount(1);

        Exchange result = nightlyValidation.send(nightlyValidation.getDefaultEndpoint(),
                e -> e.getIn().setHeaders(nightlyHeaders(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT)));

        assertThat(result.getException()).isNotNull();
        antuValidationQueue.assertIsSatisfied();
        updateStatus.assertIsSatisfied();
        assertThat(updateStatus.getExchanges().getFirst().getIn().getBody(String.class))
                .contains("PREVALIDATION")
                .contains("FAILED");
        assertThat(blobsInContainers.getOrDefault(antuExchangeContainerName, Map.of())).doesNotContainKey(FILE_HANDLE);
    }

    /**
     * The headers set by the nightly validation: it reuses a stored dataset and never classifies it, so there is
     * no FILE_TYPE header.
     */
    private static Map<String, Object> nightlyHeaders(String referential) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.PROVIDER_ID, TestConstants.PROVIDER_ID_RB_RUT);
        headers.put(Constants.CHOUETTE_REFERENTIAL, referential);
        headers.put(Constants.DATASET_REFERENTIAL, referential);
        headers.put(Constants.CORRELATION_ID, "corr-nightly");
        headers.put(Constants.FILE_HANDLE, FILE_HANDLE);
        return headers;
    }

    private byte[] validationBucketBlob() {
        byte[] blob = blobsInContainers.getOrDefault(antuExchangeContainerName, Map.of()).get(FILE_HANDLE);
        assertThat(blob).as("Expected the dataset to be copied to the validation bucket").isNotNull();
        return blob;
    }

    private byte[] internalBlob(String path) throws java.io.IOException {
        InputStream blob = internalInMemoryBlobStoreRepository.getBlob(path);
        assertThat(blob).as("Expected internal blob %s", path).isNotNull();
        return blob.readAllBytes();
    }
}
