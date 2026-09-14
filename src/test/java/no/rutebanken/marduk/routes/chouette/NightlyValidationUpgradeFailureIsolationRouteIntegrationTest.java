/*
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
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

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import org.apache.camel.Exchange;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The nightly validation upgrades the stored dataset of the DatedServiceJourney codespaces to NeTEx 1.16 before
 * sending it to Antu. A dataset that cannot be upgraded fails that provider's run - the others still run.
 */
class NightlyValidationUpgradeFailureIsolationRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    /** rb_rut is one of the netex.dsj.codespaces of the test configuration, rb_atb is not. */
    private static final String DSJ_REFERENTIAL = "rb_rut";
    private static final String OTHER_REFERENTIAL = "rb_atb";

    @EndpointInject("mock:validationCandidates")
    protected MockEndpoint validationCandidates;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @BeforeEach
    protected void setUp() throws IOException {
        super.setUp();
        validationCandidates.reset();
        when(providerRepository.getProviders()).thenReturn(List.of(
                migratingProvider(1L, DSJ_REFERENTIAL),
                migratingProvider(2L, OTHER_REFERENTIAL)));

        // the dataset of the DatedServiceJourney codespace cannot be converted, the other one is a valid archive
        internalInMemoryBlobStoreRepository.uploadBlob(lastPrevalidatedFile(DSJ_REFERENTIAL),
                new ByteArrayInputStream("not a zip archive".getBytes(StandardCharsets.UTF_8)));
        internalInMemoryBlobStoreRepository.uploadBlob(lastPrevalidatedFile(OTHER_REFERENTIAL), getTestNetexArchiveAsStream());
    }

    @Test
    void aProviderWhoseDatasetCannotBeUpgradedDoesNotStopTheOthers() throws Exception {
        AdviceWith.adviceWith(context, "antu-netex-nightly-validation", a -> {
            a.weaveByToUri("google-pubsub:(.*):AntuNetexValidationQueue").replace().to("mock:validationCandidates");
            a.interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint().to("mock:updateStatus");
        });
        AdviceWith.adviceWith(context, "antu-netex-nightly-validation-upgrade",
                a -> a.interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint().to("mock:updateStatus"));
        context.start();

        // direct: is synchronous and the splitter waits for every sub exchange, so no polling needed.
        Exchange result = context.createProducerTemplate().send("direct:triggerAntuValidationForAllProviders", e -> e.getIn().setBody(""));

        assertThat(result.getException()).as("the failed upgrade must fail the nightly run").isNotNull();
        assertThat(receivedReferentials()).containsExactly(OTHER_REFERENTIAL);
    }

    private Set<String> receivedReferentials() {
        return validationCandidates.getReceivedExchanges().stream()
                .map(e -> e.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class))
                .collect(Collectors.toSet());
    }

    private static String lastPrevalidatedFile(String referential) {
        return "last-prevalidated-files/" + referential + "-netex.zip";
    }

    private static Provider migratingProvider(long id, String referential) {
        return new Provider()
                .setId(id)
                .setChouetteInfo(new ChouetteInfo()
                        .setReferential(referential)
                        .setMigrateDataToProvider(id + 100)
                        .setEnableAutoValidation(true));
    }
}
