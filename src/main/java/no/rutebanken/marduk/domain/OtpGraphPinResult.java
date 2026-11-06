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
 * Result of the pinning or unpinning of the current graph of a serialization version. The status of the controls is
 * read separately: a failure to read it must not make an applied operation look failed.
 *
 * @param serializationId the serialization version
 * @param graph           the graph pinned, or the graph that was pinned. Null when an unpin removed a pin file that
 *                        could not be parsed
 * @param pinned          whether the serialization version is now pinned
 */
public record OtpGraphPinResult(String serializationId, String graph, boolean pinned) {
}
