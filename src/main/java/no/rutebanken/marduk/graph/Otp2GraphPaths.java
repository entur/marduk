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

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static no.rutebanken.marduk.Constants.OTP2_CURRENT_GRAPH_OBJ;
import static no.rutebanken.marduk.Constants.OTP2_GRAPH_OBJ_PREFIX;
import static no.rutebanken.marduk.Constants.OTP2_NETEX_GRAPH_DIR;

/**
 * Paths of the published OTP2 transit graphs in the graph bucket:
 * <ul>
 *     <li>{@code netex-otp2/<serialization id>/<timestamp>-Graph-otp2-<serialization id>.obj}: a published graph,
 *     one per build;</li>
 *     <li>{@code netex-otp2/<serialization id>/Graph-otp2-current.obj}: the stable copy of the current graph, loaded
 *     by the OTP instances;</li>
 *     <li>{@code netex-otp2/<serialization id>/<pointer file>} and {@code <pointer file>}: the pointer files, whose
 *     content is the path of the current graph of the serialization version and of production. The redeployment of
 *     OTP is triggered by a change of the root pointer file.</li>
 * </ul>
 */
public final class Otp2GraphPaths {

    /**
     * A timestamped graph whose directory matches the serialization id in the file name.
     * The stable copy and the pointer files do not match.
     */
    private static final Pattern VERSIONED_GRAPH_PATH = Pattern.compile(
            "^" + OTP2_NETEX_GRAPH_DIR + "/([A-Za-z0-9._-]+)/(\\d{17})-" + OTP2_GRAPH_OBJ_PREFIX + "-\\1\\.obj$");

    private static final Pattern GRAPH_TIMESTAMP = Pattern.compile("^(\\d{17})-");

    private Otp2GraphPaths() {
    }

    /**
     * A published graph.
     *
     * @param path            the path of the graph in the graph bucket
     * @param serializationId the serialization id of the graph
     * @param timestamp       the build timestamp (yyyyMMddHHmmssSSS)
     */
    public record VersionedGraph(String path, String serializationId, String timestamp) {

        public String fileName() {
            return path.substring(path.lastIndexOf('/') + 1);
        }

        public String reportVersion() {
            return Otp2GraphPaths.reportVersion(timestamp);
        }
    }

    /**
     * Parse the path of a published graph.
     *
     * @return the graph, or empty if the path is not the path of a published graph
     */
    public static Optional<VersionedGraph> parseVersionedGraph(String path) {
        if (path == null) {
            return Optional.empty();
        }
        Matcher matcher = VERSIONED_GRAPH_PATH.matcher(path);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new VersionedGraph(path, matcher.group(1), matcher.group(2)));
    }

    /**
     * The stable copy of the current graph of a serialization version.
     */
    public static String stableGraphPath(String serializationId) {
        return OTP2_NETEX_GRAPH_DIR + "/" + serializationId + "/" + OTP2_CURRENT_GRAPH_OBJ;
    }

    /**
     * The pointer file to the current graph of a serialization version.
     */
    public static String versionedPointerPath(String serializationId, String pointerFileName) {
        return OTP2_NETEX_GRAPH_DIR + "/" + serializationId + "/" + pointerFileName;
    }

    /**
     * The folder of the build report of a graph in the report bucket.
     */
    public static String reportVersion(String timestamp) {
        return OTP2_NETEX_GRAPH_DIR + "/" + timestamp + "-report";
    }

    /**
     * The serialization id of a graph path ({@code netex-otp2/<serialization id>/...}), read from its directory.
     * Unlike {@link #parseVersionedGraph(String)}, any graph published by the graph builder is accepted, for example a
     * graph of unknown version.
     *
     * @return the serialization id, or null if the path is not in a serialization version directory
     */
    public static String serializationIdOf(String graphPath) {
        if (graphPath == null) {
            return null;
        }
        String[] elements = graphPath.trim().split("/");
        if (elements.length != 3 || !OTP2_NETEX_GRAPH_DIR.equals(elements[0])) {
            return null;
        }
        return elements[1];
    }

    /**
     * The build timestamp of a graph path (yyyyMMddHHmmssSSS, the prefix of the file name).
     *
     * @return the timestamp, or null if the file name does not start with a timestamp
     */
    public static String timestampOf(String graphPath) {
        if (graphPath == null) {
            return null;
        }
        String trimmed = graphPath.trim();
        Matcher matcher = GRAPH_TIMESTAMP.matcher(trimmed.substring(trimmed.lastIndexOf('/') + 1));
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Whether the graph was built before the other graph, comparing their build timestamps.
     * Graphs without a timestamp are never considered older.
     */
    public static boolean isBuiltBefore(String graphPath, String otherGraphPath) {
        String timestamp = timestampOf(graphPath);
        String otherTimestamp = timestampOf(otherGraphPath);
        return timestamp != null && otherTimestamp != null && timestamp.compareTo(otherTimestamp) < 0;
    }

    /**
     * HTML page redirecting to the build report of a graph.
     */
    public static String reportRedirectPage(String otpReportContainerName, String reportVersion) {
        String url = "http://" + otpReportContainerName + "/" + reportVersion + "/index.html";
        return "<html>\n" +
                "<head>\n" +
                "    <meta http-equiv=\"refresh\" content=\"0; url=" + url + "\" />\n" +
                "</head>\n" +
                "</html>";
    }
}
