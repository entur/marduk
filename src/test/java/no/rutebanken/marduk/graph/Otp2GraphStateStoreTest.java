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

import no.rutebanken.marduk.graph.Otp2GraphStateStore.GraphPin;
import no.rutebanken.marduk.graph.Otp2GraphStateStore.GraphPointer;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.services.OtpGraphsBlobStoreService;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Otp2GraphStateStoreTest {

    private static final String SERIALIZATION_ID = "EN-0239";
    private static final String GRAPH = "netex-otp2/EN-0239/20261014081200123-Graph-otp2-EN-0239.obj";

    private final InMemoryMardukBlobStoreRepository repository = new InMemoryMardukBlobStoreRepository(new HashMap<>());
    private final Otp2GraphStateStore store = new Otp2GraphStateStore(new OtpGraphsBlobStoreService("graphs", repository), "current-otp2");

    @Test
    void pointerRoundTrip() throws IOException {
        assertTrue(store.readPointer(SERIALIZATION_ID).isEmpty());
        assertTrue(store.readRootPointer().isEmpty());

        GraphPointer pointer = new GraphPointer(GRAPH, false, Instant.parse("2026-10-14T08:15:00Z"), "test-user", "bad data");
        store.writePointer(SERIALIZATION_ID, pointer);
        store.writeRootPointer(pointer);

        assertEquals(Optional.of(pointer), store.readPointer(SERIALIZATION_ID));
        assertEquals(Optional.of(pointer), store.readRootPointer());
        // readable with gsutil cat: JSON with an ISO timestamp
        String content = read("netex-otp2/EN-0239/current-otp2");
        assertTrue(content.contains("\"graph\" : \"" + GRAPH + "\""), content);
        assertTrue(content.contains("\"updatedAt\" : \"2026-10-14T08:15:00Z\""), content);
        assertEquals(content, read("current-otp2"));
    }

    @Test
    void pointerWrittenBeforeTheControlsOnlyContainsTheGraphPath() {
        repository.uploadBlob("current-otp2", new ByteArrayInputStream((GRAPH + "\n").getBytes(StandardCharsets.UTF_8)));
        GraphPointer pointer = store.readRootPointer().orElseThrow();
        assertEquals(GRAPH, pointer.graph());
        assertFalse(pointer.candidate());
        assertNull(pointer.updatedAt());
        assertNull(pointer.updatedBy());
    }

    @Test
    void pinRoundTrip() {
        assertTrue(store.readPin(SERIALIZATION_ID).isEmpty());

        GraphPin pin = new GraphPin(GRAPH, Instant.parse("2026-10-14T09:00:00Z"), "test-user", "freeze");
        store.writePin(SERIALIZATION_ID, pin);
        assertEquals(Optional.of(pin), store.readPin(SERIALIZATION_ID));
        assertTrue(repository.exist("netex-otp2/EN-0239/pinned-otp2"));

        assertTrue(store.deletePin(SERIALIZATION_ID));
        assertTrue(store.readPin(SERIALIZATION_ID).isEmpty());
    }

    private String read(String name) throws IOException {
        try (InputStream content = repository.getBlob(name)) {
            return IOUtils.toString(content, StandardCharsets.UTF_8);
        }
    }
}
