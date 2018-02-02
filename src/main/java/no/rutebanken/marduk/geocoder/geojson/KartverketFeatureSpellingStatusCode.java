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

package no.rutebanken.marduk.geocoder.geojson;

import com.google.common.collect.Sets;

import java.util.Set;

public class KartverketFeatureSpellingStatusCode {
    //    U (uvurdert): the spelling has not been evaluated
    public static final String NOT_EVALUATED = "U";
    //    A (avslått): the spelling has been evaluated, and has been rejected.
    public static final String REJECTED = "A";
    //    F (foreslått): the spelling is a proposal which has not yet been decided
    public static final String PROPOSED = "F";
    //    K (vedtak påklaget): the spelling has been evaluated and accepted but a complaint has been filed; final decision pending
    public static final String COMPLAIN_FILED = "K";
    //    I (internasjonalt): the spelling concerns objects outside Norwegian territory, and are not subject to the provisions in stadnamnlova (Norwegian Place Name Act)
    public static final String INTERNATIONAL = "I";
    //    H (historisk): the spelling concerns an object that has changed name or has been deleted
    public static final String HISTORICAL = "H";

    //    V (vedtatt): the spelling has been evaluated and accepted, and is required for official use
    public static final String ACCEPTED = "V";
    //    S (samlevedtak): a part of the spelling has been evaluated and accepted as part of some "batch" decision, like "-sæter"->"-seter"
    public static final String BATCH_ACCEPTED = "S";
    //    G (godkjent): the spelling was used in official context before 1991-07-01
    public static final String APPROVED = "G";
    //  P (privat): the spelling has been decided by a private entity and not by the authorities
    public static final String PRIVATE = "P";

    private static Set<String> ACTIVE_CODES = Sets.newHashSet(ACCEPTED, BATCH_ACCEPTED, APPROVED, PRIVATE);

    public static boolean isActive(String code) {
        return ACTIVE_CODES.contains(code);
    }

}
