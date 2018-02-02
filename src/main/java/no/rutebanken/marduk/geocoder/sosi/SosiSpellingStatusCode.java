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

package no.rutebanken.marduk.geocoder.sosi;

import com.google.common.collect.Sets;

import java.util.Set;

public class SosiSpellingStatusCode {

    public static final String ACCEPTED = "vedtatt";

    public static final String INTERNATIONAL = "internasjonal";

    public static final String APPROVED = "godkjent";

    public static final String PRIVATE = "privat";

    private static Set<String> ACTIVE_CODES = Sets.newHashSet(ACCEPTED, INTERNATIONAL, APPROVED, PRIVATE);

    public static boolean isActive(String code) {
        return ACTIVE_CODES.contains(code);
    }

}

