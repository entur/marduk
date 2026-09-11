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

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.TestConstants;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.NETEX_1_16_ENTRY;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.assertDowngraded;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.mixedArchiveEntries;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.unzip;
import static no.rutebanken.marduk.routes.netex.NetexDsjConverterTest.zip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;


class NetexExportMergedRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @Produce("direct:otp2ExportMergedNetex")
    protected ProducerTemplate startRoute;

    @Value("${netex.export.stop.place.blob.path:tiamat/Full_latest.zip}")
    private String stopPlaceExportBlobPath;

    @Value("${netex.export.file.path:netex/rb_norway-aggregated-netex.zip}")
    private String netexExportMergedFilePath;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @Autowired
    private NetexDsjExportConfig netexDsjExportConfig;

    @Test
    void testExportMergedNetex() throws Exception {

        AdviceWith.adviceWith(context, "otp2-netex-export-merged-route", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));
        AdviceWith.adviceWith(context, "otp2-netex-export-merged-report-ok", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));


        // Create stop file in memory blob store
        mardukInMemoryBlobStoreRepository.uploadBlob(stopPlaceExportBlobPath, new FileInputStream("src/test/resources/no/rutebanken/marduk/routes/netex/stops.zip"));

        // Create provider netex export in memory blob store
        mardukInMemoryBlobStoreRepository.uploadBlob(BLOBSTORE_PATH_OUTBOUND + "netex/rb_rut-aggregated-netex.zip", new FileInputStream("src/test/resources/no/rutebanken/marduk/routes/file/beans/netex.zip"));

        updateStatus.expectedMessageCount(2);

        context.start();

        startRoute.requestBody(null);

        updateStatus.assertIsSatisfied();

        List<JobEvent> events = updateStatus.getExchanges().stream().map(e -> JobEvent.fromString(e.getIn().getBody().toString())).toList();
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE_PUBLISH.equals(je.getDomain()) && JobEvent.State.STARTED.equals(je.getState())));
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE_PUBLISH.equals(je.getDomain()) && JobEvent.State.OK.equals(je.getState())));

        assertThat(mardukInMemoryBlobStoreRepository.getBlob(BLOBSTORE_PATH_OUTBOUND + netexExportMergedFilePath)).as("Expected merged netex file to have been uploaded").isNotNull();
    }


    /**
     * With the dual DatedServiceJourney export enabled, the aggregated export is built from the new and legacy
     * per-provider exports and the default folder receives the default variant.
     */
    @Test
    void testExportMergedNetexDsjVariants() throws Exception {
        AdviceWith.adviceWith(context, "otp2-netex-export-merged-route", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));
        AdviceWith.adviceWith(context, "otp2-netex-export-merged-report-ok", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));

        // a provider whose export is published (not migrated to another provider)
        when(providerRepository.getProviders()).thenReturn(List.of(provider(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT, TestConstants.PROVIDER_ID_RB_RUT, null)));

        mardukInMemoryBlobStoreRepository.uploadBlob(stopPlaceExportBlobPath, new FileInputStream("src/test/resources/no/rutebanken/marduk/routes/netex/stops.zip"));
        // the new variant of the provider export is a NeTEx 1.16 dataset, the legacy variant its downgraded copy
        Map<String, byte[]> newEntries = mixedArchiveEntries();
        String newExportPath = netexDsjExportConfig.newExportPath(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT);
        String legacyExportPath = netexDsjExportConfig.legacyExportPath(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT);
        mardukInMemoryBlobStoreRepository.uploadBlob(newExportPath, new ByteArrayInputStream(zip(newEntries)));
        java.io.ByteArrayOutputStream legacyNetex = new java.io.ByteArrayOutputStream();
        NetexDsjConverter.downgradeXml(NetexDsjConverterTest.NETEX_1_16_FIXTURE.toFile(), legacyNetex, Long.MAX_VALUE);
        Map<String, byte[]> legacyEntries = new java.util.LinkedHashMap<>(newEntries);
        legacyEntries.put(NETEX_1_16_ENTRY, legacyNetex.toByteArray());
        mardukInMemoryBlobStoreRepository.uploadBlob(legacyExportPath, new ByteArrayInputStream(zip(legacyEntries)));

        updateStatus.expectedMessageCount(2);
        context.start();
        startRoute.requestBody(null);
        updateStatus.assertIsSatisfied();

        String mergedFileName = "rb_norway-aggregated-netex.zip";
        Map<String, byte[]> newMerged = unzip(readBlob(netexDsjExportConfig.variantPath(NetexDsjExportConfig.Variant.NEW, mergedFileName)));
        Map<String, byte[]> legacyMerged = unzip(readBlob(netexDsjExportConfig.variantPath(NetexDsjExportConfig.Variant.LEGACY, mergedFileName)));
        byte[] defaultMerged = readBlob(BLOBSTORE_PATH_OUTBOUND + netexExportMergedFilePath);

        assertThat(newMerged.keySet()).contains(NETEX_1_16_ENTRY, "_stops.xml");
        assertThat(new String(newMerged.get(NETEX_1_16_ENTRY), StandardCharsets.UTF_8)).contains("version=\"1.16:");
        assertThat(legacyMerged.keySet()).contains(NETEX_1_16_ENTRY, "_stops.xml");
        assertDowngraded(new String(legacyMerged.get(NETEX_1_16_ENTRY), StandardCharsets.UTF_8));
        // the stop place export contains no DatedServiceJourney and is used as is in both variants
        assertThat(legacyMerged.get("_stops.xml")).isEqualTo(newMerged.get("_stops.xml"));
        // the default folder receives the legacy variant (netex.export.dsj.default.variant=legacy in the test configuration)
        assertThat(defaultMerged).isEqualTo(readBlob(netexDsjExportConfig.variantPath(NetexDsjExportConfig.Variant.LEGACY, mergedFileName)));
    }

    /**
     * A provider that has not published since the dual export was enabled exists only in the default folder:
     * its export is distributed to the new and legacy folders before the aggregated exports are built.
     */
    @Test
    void testExportMergedNetexDsjVariantsDistributesMissingProviderExports() throws Exception {
        AdviceWith.adviceWith(context, "otp2-netex-export-merged-route", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));
        AdviceWith.adviceWith(context, "otp2-netex-export-merged-report-ok", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));

        when(providerRepository.getProviders()).thenReturn(List.of(provider(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT, TestConstants.PROVIDER_ID_RB_RUT, null)));

        mardukInMemoryBlobStoreRepository.uploadBlob(stopPlaceExportBlobPath, new FileInputStream("src/test/resources/no/rutebanken/marduk/routes/netex/stops.zip"));
        // the NeTEx 1.16 export exists only in the default folder
        mardukInMemoryBlobStoreRepository.uploadBlob(netexDsjExportConfig.defaultExportPath(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT), new ByteArrayInputStream(zip(mixedArchiveEntries())));

        updateStatus.expectedMessageCount(2);
        context.start();
        startRoute.requestBody(null);
        updateStatus.assertIsSatisfied();

        // the provider export has been distributed
        assertThat(mardukInMemoryBlobStoreRepository.getBlob(netexDsjExportConfig.newExportPath(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT))).isNotNull();
        assertThat(mardukInMemoryBlobStoreRepository.getBlob(netexDsjExportConfig.legacyExportPath(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT))).isNotNull();

        String mergedFileName = "rb_norway-aggregated-netex.zip";
        Map<String, byte[]> newMerged = unzip(readBlob(netexDsjExportConfig.variantPath(NetexDsjExportConfig.Variant.NEW, mergedFileName)));
        Map<String, byte[]> legacyMerged = unzip(readBlob(netexDsjExportConfig.variantPath(NetexDsjExportConfig.Variant.LEGACY, mergedFileName)));
        assertThat(new String(newMerged.get(NETEX_1_16_ENTRY), StandardCharsets.UTF_8)).contains("version=\"1.16:");
        assertDowngraded(new String(legacyMerged.get(NETEX_1_16_ENTRY), StandardCharsets.UTF_8));
        assertThat(readBlob(BLOBSTORE_PATH_OUTBOUND + netexExportMergedFilePath)).isEqualTo(readBlob(netexDsjExportConfig.variantPath(NetexDsjExportConfig.Variant.LEGACY, mergedFileName)));
    }

    private byte[] readBlob(String path) throws java.io.IOException {
        InputStream blob = mardukInMemoryBlobStoreRepository.getBlob(path);
        assertThat(blob).as("Expected blob %s", path).isNotNull();
        return blob.readAllBytes();
    }
}
