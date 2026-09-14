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

package no.rutebanken.marduk.netex.conversion;

/**
 * The NeTEx schema versions involved in the conversions of this package. Only the versions on either side of a
 * supported conversion are listed; the names match the {@code NetexVersion} enum of netex-java-model, which the tests
 * use to validate the converted documents against the schemas.
 */
public enum NetexVersion {
    v1_15,
    v1_16
}
