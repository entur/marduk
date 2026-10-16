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

package no.rutebanken.marduk.domain;

/**
 * Request to roll out a published graph.
 *
 * @param graph  the path of the graph in the graph bucket ({@code netex-otp2/<serialization id>/<timestamp>-Graph-otp2-<serialization id>.obj})
 * @param pin    whether to pin the graph, so that new builds do not replace it. Otherwise a pin on the serialization
 *               version is removed.
 * @param reason why the graph is rolled out
 */
public record OtpGraphRolloutRequest(String graph, boolean pin, String reason) {
}
