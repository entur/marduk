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

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;

/**
 * State of the emergency controls of the OTP graph publication.
 *
 * @param builder       the state of the graph builder
 * @param production    the serialization version used in production, null if no graph is published yet
 * @param pins          the pinned graphs
 * @param recentActions the latest control operations, most recent first
 */
public record OtpGraphControlStatus(Builder builder, Production production, List<Pin> pins, List<Action> recentActions) {

    /**
     * @param paused               whether the graph builder is paused
     * @param pausedBy             the user who paused the graph builder
     * @param pausedAt             when the graph builder was paused
     * @param reason               why the graph builder was paused
     * @param deferredTransitBuilds the number of transit graph build requests waiting for the graph builder to resume
     * @param deferredStreetBuilds  the number of street graph build requests waiting for the graph builder to resume
     */
    public record Builder(boolean paused,
                          String pausedBy,
                          @JsonFormat(without = JsonFormat.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS) Instant pausedAt,
                          String reason,
                          int deferredTransitBuilds,
                          int deferredStreetBuilds) {
    }

    /**
     * @param serializationId the serialization version of the graph referenced by the root pointer file
     * @param currentGraph    the current graph of this serialization version, loaded by the OTP instances
     * @param pinned          whether the current graph is pinned
     */
    public record Production(String serializationId, String currentGraph, boolean pinned) {
    }

    /**
     * @param serializationId the pinned serialization version
     * @param graph           the pinned graph
     * @param currentGraph    the current graph of this serialization version
     */
    public record Pin(String serializationId,
                      String graph,
                      String pinnedBy,
                      @JsonFormat(without = JsonFormat.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS) Instant pinnedAt,
                      String reason,
                      String currentGraph) {
    }

    public record Action(String action,
                         String serializationId,
                         String graph,
                         String performedBy,
                         @JsonFormat(without = JsonFormat.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS) Instant performedAt,
                         String reason) {
    }
}
