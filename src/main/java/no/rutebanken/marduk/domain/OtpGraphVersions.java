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
 * The published OTP2 transit graphs, grouped by serialization version, production first.
 */
public record OtpGraphVersions(List<SerializationVersion> serializationVersions) {

    /**
     * @param serializationId the serialization version
     * @param production      whether this serialization version is used in production
     * @param currentGraph    the current graph of this serialization version, null if none
     * @param pinnedGraph     the pinned graph of this serialization version, null if none
     * @param totalGraphs     the number of published graphs of this serialization version
     * @param graphs          the latest published graphs, most recent first
     */
    public record SerializationVersion(String serializationId,
                                       boolean production,
                                       String currentGraph,
                                       String pinnedGraph,
                                       int totalGraphs,
                                       List<Graph> graphs) {
    }

    /**
     * @param graph        the path of the graph in the graph bucket, to be passed to the rollout operation
     * @param timestamp    the build timestamp (yyyyMMddHHmmssSSS)
     * @param creationDate when the graph was published
     * @param size         the size of the graph in bytes
     * @param current      whether this is the current graph of its serialization version
     * @param pinned       whether this graph is pinned
     */
    public record Graph(String graph,
                        String fileName,
                        String timestamp,
                        @JsonFormat(without = JsonFormat.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS) Instant creationDate,
                        Long size,
                        boolean current,
                        boolean pinned) {
    }
}
