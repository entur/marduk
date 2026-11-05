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

package no.rutebanken.marduk.graph;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import no.rutebanken.marduk.services.OtpGraphsBlobStoreService;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

/**
 * State of the OTP2 graph publication, kept in the graph bucket next to the graph files:
 * <ul>
 *     <li>the pointer file of a serialization version records its current graph, the graph copied to the stable name
 *     loaded by the OTP instances. It is written after the stable copy, so that it never names a graph that is not
 *     in place;</li>
 *     <li>the pin marker of a serialization version records that its current graph must not be replaced by new
 *     builds;</li>
 *     <li>the root pointer file records the current graph of the serialization version used in production. The OTP
 *     redeployment job restarts the OTP instances when its content changes.</li>
 * </ul>
 * The files are small JSON documents. A pointer file written before the controls were introduced only contains the
 * path of the graph: it is read as a pointer to a production build.
 */
@Component
public class Otp2GraphStateStore {

    /**
     * The current graph of a serialization version.
     *
     * @param graph     the path of the graph in the graph bucket
     * @param candidate whether the graph was built by a candidate version of OTP
     * @param updatedAt when the graph was made current
     * @param updatedBy the user who made the graph current, or the system user for a build
     * @param reason    the reason given by the user, null for a build
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphPointer(String graph,
                               boolean candidate,
                               @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt,
                               String updatedBy,
                               String reason) {
    }

    /**
     * The pin of a serialization version.
     *
     * @param graph the pinned graph, the current graph of the serialization version when the pin was set
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GraphPin(String graph,
                           @JsonFormat(shape = JsonFormat.Shape.STRING) Instant pinnedAt,
                           String pinnedBy,
                           String reason) {
    }

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getSharedObjectMapper();

    private final OtpGraphsBlobStoreService otpGraphsBlobStoreService;
    private final String pointerFileName;

    public Otp2GraphStateStore(OtpGraphsBlobStoreService otpGraphsBlobStoreService,
                               @Value("${otp2.graph.current.file:current-otp2}") String pointerFileName) {
        this.otpGraphsBlobStoreService = otpGraphsBlobStoreService;
        this.pointerFileName = pointerFileName;
    }

    /**
     * The name of the pointer files, in the root of the bucket and in the folder of each serialization version.
     */
    public String pointerFileName() {
        return pointerFileName;
    }

    public String versionedPointerPath(String serializationId) {
        return Otp2GraphPaths.versionedPointerPath(serializationId, pointerFileName);
    }

    public Optional<GraphPointer> readPointer(String serializationId) {
        return readPointerFile(versionedPointerPath(serializationId));
    }

    public Optional<GraphPointer> readRootPointer() {
        return readPointerFile(pointerFileName);
    }

    public void writePointer(String serializationId, GraphPointer pointer) {
        write(versionedPointerPath(serializationId), pointer);
    }

    public void writeRootPointer(GraphPointer pointer) {
        write(pointerFileName, pointer);
    }

    public Optional<GraphPin> readPin(String serializationId) {
        return read(Otp2GraphPaths.pinPath(serializationId))
                .map(content -> parse(content, GraphPin.class, Otp2GraphPaths.pinPath(serializationId)));
    }

    public void writePin(String serializationId, GraphPin pin) {
        write(Otp2GraphPaths.pinPath(serializationId), pin);
    }

    /**
     * @return true if a pin marker was deleted
     */
    public boolean deletePin(String serializationId) {
        return otpGraphsBlobStoreService.deleteBlob(Otp2GraphPaths.pinPath(serializationId));
    }

    private Optional<GraphPointer> readPointerFile(String path) {
        return read(path).map(content -> parsePointer(content, path));
    }

    /**
     * A pointer file written before the controls were introduced only contains the path of the current graph. Such a
     * file was rewritten by candidate builds as well as by production builds, so whether the graph is a candidate
     * build is unknown: it is read as a production build. In a serialization version that only has candidate builds,
     * for example the next one, later candidate builds are then not promoted until a production build of that
     * serialization version replaces the pointer file.
     */
    static GraphPointer parsePointer(String content, String path) {
        String trimmed = content.trim();
        if (trimmed.startsWith("{")) {
            return parse(trimmed, GraphPointer.class, path);
        }
        return new GraphPointer(trimmed, false, null, null, null);
    }

    private Optional<String> read(String path) {
        if (!Boolean.TRUE.equals(otpGraphsBlobStoreService.blobExists(path))) {
            return Optional.empty();
        }
        InputStream inputStream = otpGraphsBlobStoreService.getBlob(path);
        if (inputStream == null) {
            return Optional.empty();
        }
        try (inputStream) {
            return Optional.of(IOUtils.toString(inputStream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new MardukException("Failed to read " + path, e);
        }
    }

    private void write(String path, Object document) {
        try {
            byte[] content = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(document);
            otpGraphsBlobStoreService.uploadBlobWithoutVersionHeader(path, new ByteArrayInputStream(content));
        } catch (JsonProcessingException e) {
            throw new MardukException("Failed to serialize " + path, e);
        }
    }

    private static <T> T parse(String content, Class<T> type, String path) {
        try {
            return OBJECT_MAPPER.readValue(content, type);
        } catch (JsonProcessingException e) {
            throw new UnreadableStateFileException(path, e);
        }
    }

    /**
     * A pointer or pin file whose content cannot be parsed, for example after a truncated manual copy.
     */
    public static class UnreadableStateFileException extends MardukException {

        private static final long serialVersionUID = 1L;

        private final String path;

        UnreadableStateFileException(String path, JsonProcessingException cause) {
            super("Failed to parse " + path + ": " + cause.getMessage(), cause);
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }
}
