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

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Otp2GraphPathsTest {

    private static final String GRAPH = "netex-otp2/EN-0239/20261014081200123-Graph-otp2-EN-0239.obj";

    @Test
    void parsePublishedGraph() {
        Optional<Otp2GraphPaths.VersionedGraph> graph = Otp2GraphPaths.parseVersionedGraph(GRAPH);
        assertTrue(graph.isPresent());
        assertEquals("EN-0239", graph.get().serializationId());
        assertEquals("20261014081200123", graph.get().timestamp());
        assertEquals("20261014081200123-Graph-otp2-EN-0239.obj", graph.get().fileName());
        assertEquals("netex-otp2/20261014081200123-report", graph.get().reportVersion());
    }

    @Test
    void rejectFilesThatAreNotPublishedGraphs() {
        assertFalse(Otp2GraphPaths.parseVersionedGraph(null).isPresent());
        assertFalse(Otp2GraphPaths.parseVersionedGraph("netex-otp2/EN-0239/Graph-otp2-current.obj").isPresent());
        assertFalse(Otp2GraphPaths.parseVersionedGraph("netex-otp2/EN-0239/current-otp2").isPresent());
        assertFalse(Otp2GraphPaths.parseVersionedGraph("current-otp2").isPresent());
        // the serialization id of the directory does not match the file name
        assertFalse(Otp2GraphPaths.parseVersionedGraph("netex-otp2/EN-0238/20261014081200123-Graph-otp2-EN-0239.obj").isPresent());
        assertFalse(Otp2GraphPaths.parseVersionedGraph("netex-otp2/../20261014081200123-Graph-otp2-EN-0239.obj").isPresent());
        assertFalse(Otp2GraphPaths.parseVersionedGraph("other/EN-0239/20261014081200123-Graph-otp2-EN-0239.obj").isPresent());
        assertFalse(Otp2GraphPaths.parseVersionedGraph(GRAPH + ".tmp").isPresent());
    }

    @Test
    void derivedPaths() {
        assertEquals("netex-otp2/EN-0239/Graph-otp2-current.obj", Otp2GraphPaths.stableGraphPath("EN-0239"));
        assertEquals("netex-otp2/EN-0239/current-otp2", Otp2GraphPaths.versionedPointerPath("EN-0239", "current-otp2"));
    }

    @Test
    void serializationIdOfAPointerContent() {
        assertEquals("EN-0239", Otp2GraphPaths.serializationIdOf(GRAPH));
        assertEquals("EN-0239", Otp2GraphPaths.serializationIdOf(GRAPH + "\n"));
        assertEquals("unknown-version", Otp2GraphPaths.serializationIdOf("netex-otp2/unknown-version/20261014081200123-Graph-otp2.obj"));
        assertNull(Otp2GraphPaths.serializationIdOf(null));
        assertNull(Otp2GraphPaths.serializationIdOf("current-otp2"));
    }

    @Test
    void buildOrder() {
        String older = "netex-otp2/EN-0239/20261013081200123-Graph-otp2-EN-0239.obj";
        assertTrue(Otp2GraphPaths.isBuiltBefore(older, GRAPH));
        assertFalse(Otp2GraphPaths.isBuiltBefore(GRAPH, older));
        assertFalse(Otp2GraphPaths.isBuiltBefore(GRAPH, GRAPH));
        assertFalse(Otp2GraphPaths.isBuiltBefore(GRAPH, null));
        assertFalse(Otp2GraphPaths.isBuiltBefore("netex-otp2/EN-0239/Graph-otp2-current.obj", GRAPH));
    }
}
