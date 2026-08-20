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
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Which providers the nightly validation routes pick up. The antu prevalidation route takes
 * providers that migrate their data onwards, the Chouette level 2 route takes the rest, and both
 * skip providers without auto validation or with a blank referential.
 */
class ChouetteNightlyValidationCandidatesRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @EndpointInject("mock:validationCandidates")
    protected MockEndpoint validationCandidates;

    @BeforeEach
    protected void setUp() throws IOException {
        super.setUp();
        validationCandidates.reset();
        when(providerRepository.getProviders()).thenReturn(List.of(
                providerWith(1L, "rb_migrating", 2L, true),
                providerWith(2L, "rb_final", null, true),
                providerWith(3L, "rb_no_auto_validation", null, false),
                providerWith(4L, "   ", null, true)));
    }

    static Stream<Arguments> routes() {
        return Stream.of(
                Arguments.of("antu prevalidation takes providers with a migration target",
                        "trigger-antu-validation-for-all-providers",
                        "direct:triggerAntuValidationForAllProviders",
                        "direct:antuNetexNightlyValidation",
                        "rb_migrating"),
                Arguments.of("chouette level 2 takes providers without a migration target",
                        "chouette-validate-level2-all-providers",
                        "direct:chouetteValidateLevel2ForAllProviders",
                        "google-pubsub:(.*):ChouetteValidationQueue",
                        "rb_final"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("routes")
    void nightlyValidationSelectsOnlyCandidates(
            String scenario,
            String routeId,
            String routeUri,
            String downstreamUri,
            String expectedReferential) throws Exception {

        AdviceWith.adviceWith(context, routeId,
                a -> a.weaveByToUri(downstreamUri).replace().to("mock:validationCandidates"));
        context.start();

        // direct: is synchronous and the splitter waits for every sub exchange, so no polling needed.
        context.createProducerTemplate().sendBody(routeUri, "");

        assertEquals(Set.of(expectedReferential), receivedReferentials(), scenario);
    }

    private Set<String> receivedReferentials() {
        return validationCandidates.getReceivedExchanges().stream()
                .map(e -> e.getIn().getHeader(CHOUETTE_REFERENTIAL, String.class))
                .collect(Collectors.toSet());
    }

    private static Provider providerWith(long id, String referential, Long migrateDataToProvider, boolean enableAutoValidation) {
        return new Provider()
                .setId(id)
                .setChouetteInfo(new ChouetteInfo()
                        .setReferential(referential)
                        .setMigrateDataToProvider(migrateDataToProvider)
                        .setEnableAutoValidation(enableAutoValidation));
    }
}
