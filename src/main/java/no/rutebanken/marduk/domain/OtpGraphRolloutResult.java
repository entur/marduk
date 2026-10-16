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
 * Result of the rollout of a graph.
 *
 * @param graph            the rolled out graph
 * @param serializationId  the serialization version of the graph
 * @param production       whether the graph belongs to the serialization version used in production
 * @param restartTriggered whether the redeployment of the OTP instances was triggered
 * @param pinned           whether the graph is pinned
 */
public record OtpGraphRolloutResult(String graph, String serializationId, boolean production, boolean restartTriggered, boolean pinned) {
}
