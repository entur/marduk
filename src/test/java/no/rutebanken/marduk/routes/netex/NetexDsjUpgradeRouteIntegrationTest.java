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
import no.rutebanken.marduk.routes.file.FileType;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.NETEX_1_15_FIXTURE;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.NETEX_1_16_ENTRY;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.OLD_NETEX_ENTRY;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.README_ENTRY;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.assertUpgraded;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.mixedArchiveEntries;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.unzip;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.zip;
import static org.assertj.core.api.Assertions.assertThat;

class NetexDsjUpgradeRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    private static final String NETEX_1_15_ENTRY = "VYG_netex-1.15.xml";

    @Produce("direct:upgradeNetexDatasetIfNeeded")
    protected ProducerTemplate upgradeIfNeeded;

    private byte[] datasetWithFilesToUpgrade;
    private byte[] datasetWithoutFilesToUpgrade;

    @BeforeEach
    void prepare() throws Exception {
        // mixedArchiveEntries() holds a NeTEx 1.16 file, a NeTEx file declaring an older version and a non-XML file
        Map<String, byte[]> entries = new LinkedHashMap<>(mixedArchiveEntries());
        Map<String, byte[]> upToDateEntries = new LinkedHashMap<>(entries);
        upToDateEntries.remove(OLD_NETEX_ENTRY);
        datasetWithoutFilesToUpgrade = zip(upToDateEntries);
        entries.put(NETEX_1_15_ENTRY, Files.readAllBytes(NETEX_1_15_FIXTURE));
        datasetWithFilesToUpgrade = zip(entries);
        context.start();
    }

    @Test
    void uploadedFilesOlderThanNetex116AreUpgradedInPlace() throws Exception {
        String fileHandle = "inbound/received/rb_rut/netex.zip";
        internalInMemoryBlobStoreRepository.uploadBlob(fileHandle, new ByteArrayInputStream(datasetWithFilesToUpgrade));

        Exchange result = upgradeIfNeeded.send(upgradeIfNeeded.getDefaultEndpoint(), e -> e.getIn().setHeaders(headers(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT, fileHandle, FileType.NETEXPROFILE)));

        assertThat(result.getException()).isNull();
        assertThat(result.getIn().getHeader(Constants.FILE_HANDLE)).isEqualTo(fileHandle);
        Map<String, byte[]> upgraded = unzip(internalBlob(fileHandle));
        assertThat(upgraded.keySet()).containsExactly(NETEX_1_16_ENTRY, OLD_NETEX_ENTRY, README_ENTRY, NETEX_1_15_ENTRY);
        assertUpgraded(new String(upgraded.get(NETEX_1_15_ENTRY), StandardCharsets.UTF_8));
        // a file declaring a version older than 1.15 is upgraded as well
        assertThat(new String(upgraded.get(OLD_NETEX_ENTRY), StandardCharsets.UTF_8)).contains("version=\"1.16\"");
        assertThat(new String(upgraded.get(NETEX_1_16_ENTRY), StandardCharsets.UTF_8)).contains("version=\"1.16:");
    }

    @Test
    void datasetsWithoutFilesOlderThanNetex116AreKeptAsIs() throws Exception {
        String fileHandle = "inbound/received/rb_rut/netex-1.16.zip";
        internalInMemoryBlobStoreRepository.uploadBlob(fileHandle, new ByteArrayInputStream(datasetWithoutFilesToUpgrade));

        Exchange result = upgradeIfNeeded.send(upgradeIfNeeded.getDefaultEndpoint(), e -> e.getIn().setHeaders(headers(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT, fileHandle, FileType.NETEXPROFILE)));

        assertThat(result.getException()).isNull();
        assertThat(internalBlob(fileHandle)).isEqualTo(datasetWithoutFilesToUpgrade);
    }

    @Test
    void datasetsOfOtherCodespacesAreKeptAsIs() throws Exception {
        String fileHandle = "inbound/received/rb_atb/netex.zip";
        internalInMemoryBlobStoreRepository.uploadBlob(fileHandle, new ByteArrayInputStream(datasetWithFilesToUpgrade));

        Exchange result = upgradeIfNeeded.send(upgradeIfNeeded.getDefaultEndpoint(), e -> e.getIn().setHeaders(headers("rb_atb", fileHandle, FileType.NETEXPROFILE)));

        assertThat(result.getException()).isNull();
        assertThat(internalBlob(fileHandle)).isEqualTo(datasetWithFilesToUpgrade);
    }

    @Test
    void nonNetexFilesAreKeptAsIs() throws Exception {
        String fileHandle = "inbound/received/rb_rut/gtfs.zip";
        internalInMemoryBlobStoreRepository.uploadBlob(fileHandle, new ByteArrayInputStream(datasetWithFilesToUpgrade));

        Exchange result = upgradeIfNeeded.send(upgradeIfNeeded.getDefaultEndpoint(), e -> e.getIn().setHeaders(headers(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT, fileHandle, FileType.GTFS)));

        assertThat(result.getException()).isNull();
        assertThat(internalBlob(fileHandle)).isEqualTo(datasetWithFilesToUpgrade);
    }

    private static Map<String, Object> headers(String referential, String fileHandle, FileType fileType) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.PROVIDER_ID, TestConstants.PROVIDER_ID_RB_RUT);
        headers.put(Constants.CHOUETTE_REFERENTIAL, referential);
        headers.put(Constants.CORRELATION_ID, "corr-upgrade");
        headers.put(Constants.FILE_HANDLE, fileHandle);
        headers.put(Constants.FILE_TYPE, fileType.name());
        return headers;
    }

    private byte[] internalBlob(String path) throws java.io.IOException {
        InputStream blob = internalInMemoryBlobStoreRepository.getBlob(path);
        assertThat(blob).as("Expected internal blob %s", path).isNotNull();
        return blob.readAllBytes();
    }
}
