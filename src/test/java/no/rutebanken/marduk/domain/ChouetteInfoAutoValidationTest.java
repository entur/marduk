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

package no.rutebanken.marduk.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Selects which providers the nightly validation routes pick up. A blank referential must be
 * excluded: Camel's Simple language, which used to express this filter, treated a blank string as
 * false.
 */
class ChouetteInfoAutoValidationTest {

    static Stream<Arguments> scenarios() {
        return Stream.of(
                Arguments.of("auto validation off", false, "rb_tst", false),
                Arguments.of("enabled with a referential", true, "rb_tst", true),
                Arguments.of("referential missing", true, null, false),
                Arguments.of("referential empty", true, "", false),
                Arguments.of("referential whitespace only", true, "   ", false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void autoValidationCandidateSelection(
            String scenario,
            boolean enableAutoValidation,
            String referential,
            boolean expected) {
        ChouetteInfo chouetteInfo = new ChouetteInfo()
                .setEnableAutoValidation(enableAutoValidation)
                .setReferential(referential);

        assertEquals(expected, chouetteInfo.isAutoValidationCandidate(), scenario);
    }
}
