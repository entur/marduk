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
 * @param production    the serialization version used in production, null if no production graph is published yet
 * @param pins          the pinned graphs
 * @param recentActions the latest control operations, most recent first
 * @param skippedBuilds the latest graphs built but not promoted (pinned serialization version, candidate build
 *                      where the current graph is a production build, older than the current graph, failed
 *                      promotion), most recent first
 * @param unreadableFiles the pointer and pin files of the graph bucket that cannot be parsed. They are left out of
 *                        this status: a serialization version whose pointer file is unreadable has no current graph,
 *                        one whose pin file is unreadable is not listed as pinned, and production is null if the
 *                        root pointer file is unreadable. They must be repaired or deleted in the bucket.
 */
public record OtpGraphControlStatus(Production production,
                                    List<Pin> pins,
                                    List<Action> recentActions,
                                    List<Action> skippedBuilds,
                                    List<String> unreadableFiles) {

    /**
     * @param serializationId the serialization version used in production: the serialization version of the graph
     *                        the root pointer file points at, written by every production build
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
