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

import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.domain.OtpGraphControlStatus;
import no.rutebanken.marduk.domain.OtpGraphRolloutResult;
import no.rutebanken.marduk.domain.OtpGraphVersions;
import no.rutebanken.marduk.exceptions.GraphControlException;
import no.rutebanken.marduk.graph.GraphControlRepository.AuditEntry;
import no.rutebanken.marduk.graph.GraphControlRepository.LockWait;
import no.rutebanken.marduk.graph.Otp2GraphStateStore.GraphPin;
import no.rutebanken.marduk.graph.Otp2GraphStateStore.GraphPointer;
import no.rutebanken.marduk.services.OtpGraphsBlobStoreService;
import no.rutebanken.marduk.services.OtpReportBlobStoreService;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

import static no.rutebanken.marduk.Constants.OTP2_GRAPH_REPORT_INDEX_FILE;
import static no.rutebanken.marduk.Constants.OTP2_NETEX_GRAPH_DIR;

/**
 * Emergency controls of the OTP2 transit graph publication: roll out a published graph, that is make it the current
 * graph of its serialization version (the stable copy loaded by the OTP instances), and pin the current graph of a
 * serialization version so that new builds do not replace it.
 * <p>
 * The state of the publication lives in the graph bucket, next to the graph files (see {@link Otp2GraphStateStore}):
 * the pointer file of a serialization version is written after its stable copy, so that it never names a graph that
 * is not in place, and a pin is a marker file. Nothing is recorded before it is true: every operation is idempotent,
 * and the remedy for a failure is to run the same operation again. The serialization version used in production is
 * the one the root pointer file points at, which is written by every production build: no state is kept about it.
 * <p>
 * The promotion of a new graph by the graph builder and the control operations lock the same database row, so that
 * they are applied one at a time across the cluster. The database also keeps the audit trail.
 */
@Service
public class Otp2GraphControlService {

    public enum Promotion {
        PROMOTED, SKIPPED_PINNED, SKIPPED_OLDER, SKIPPED_CANDIDATE, FAILED
    }

    static final String ACTION_ROLLOUT = "ROLLOUT";
    static final String ACTION_ROLLOUT_AND_PIN = "ROLLOUT_AND_PIN";
    static final String ACTION_PIN = "PIN";
    static final String ACTION_UNPIN = "UNPIN";
    static final String ACTION_BUILD_NOT_PROMOTED = "BUILD_NOT_PROMOTED";

    static final String SYSTEM_USER = "marduk";

    private static final int MAX_REASON_LENGTH = 1000;
    private static final int RECENT_ACTIONS = 20;
    private static final int RECENT_SKIPPED_BUILDS = 10;
    /**
     * Builds that are not promoted are audited at every provider publication while a pin is set: they are kept for
     * a limited time, unlike the control operations.
     */
    private static final Duration SKIPPED_BUILD_RETENTION = Duration.ofDays(30);

    private static final Logger LOGGER = LoggerFactory.getLogger(Otp2GraphControlService.class);

    private final GraphControlRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final Otp2GraphStateStore stateStore;
    private final OtpGraphsBlobStoreService otpGraphsBlobStoreService;
    private final OtpReportBlobStoreService otpReportBlobStoreService;
    private final String otpReportContainerName;

    public Otp2GraphControlService(GraphControlRepository repository,
                                   PlatformTransactionManager transactionManager,
                                   Otp2GraphStateStore stateStore,
                                   OtpGraphsBlobStoreService otpGraphsBlobStoreService,
                                   OtpReportBlobStoreService otpReportBlobStoreService,
                                   @Value("${blobstore.gcs.otpreport.container.name}") String otpReportContainerName) {
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.stateStore = stateStore;
        this.otpGraphsBlobStoreService = otpGraphsBlobStoreService;
        this.otpReportBlobStoreService = otpReportBlobStoreService;
        this.otpReportContainerName = otpReportContainerName;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Graph builder hook
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Promote a graph published by the graph builder, that is make it the current graph of its serialization version,
     * unless the serialization version is pinned, a more recent graph is already current, or the graph is a candidate
     * build and the current graph is a production build: the stable copy loaded by the production OTP instances only
     * ever contains production builds, even when a candidate build shares their serialization version.
     * A production build also writes the root pointer file, which triggers the redeployment of the OTP instances,
     * and the report index.
     *
     * @param graphPath       the path of the published graph
     * @param serializationId the serialization version of the graph
     * @param candidate       whether the graph was built by a candidate version of OTP
     * @param reportVersion   the folder of the build report in the report bucket
     */
    public Promotion promoteBuiltGraph(String graphPath, String serializationId, boolean candidate, String reportVersion) {
        return transactionTemplate.execute(status -> {
            repository.lockControlRow(LockWait.BUILD);

            Optional<GraphPin> pin = stateStore.readPin(serializationId);
            if (pin.isPresent()) {
                audit(ACTION_BUILD_NOT_PROMOTED, serializationId, graphPath, SYSTEM_USER, "The serialization version is pinned to " + pin.get().graph());
                return Promotion.SKIPPED_PINNED;
            }

            Optional<GraphPointer> current = stateStore.readPointer(serializationId);
            if (candidate && current.isPresent() && !current.get().candidate()) {
                audit(ACTION_BUILD_NOT_PROMOTED, serializationId, graphPath, SYSTEM_USER, "Candidate build: the current graph " + current.get().graph() + " is a production build");
                return Promotion.SKIPPED_CANDIDATE;
            }
            // a production build is not held back by an overlapping candidate build of the same serialization version
            if (current.isPresent() && (candidate || !current.get().candidate())
                    && Otp2GraphPaths.isBuiltBefore(graphPath, current.get().graph())) {
                audit(ACTION_BUILD_NOT_PROMOTED, serializationId, graphPath, SYSTEM_USER, "A more recent graph is current: " + current.get().graph());
                return Promotion.SKIPPED_OLDER;
            }

            makeCurrent(graphPath, serializationId, candidate, reportVersion, SYSTEM_USER, null, !candidate);
            return Promotion.PROMOTED;
        });
    }

    /**
     * Record that the promotion of a built graph failed: the graph is published, and available for rollout, but is
     * not current. Best effort, since the failure may come from the database itself.
     */
    public void recordPromotionFailure(String graphPath, String serializationId, Exception failure) {
        try {
            transactionTemplate.executeWithoutResult(status ->
                    audit(ACTION_BUILD_NOT_PROMOTED, serializationId, graphPath, SYSTEM_USER, "The promotion failed: " + failure.getMessage()));
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to record the promotion failure of the OTP2 graph {}", graphPath, e);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Control operations
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Make a published graph the current graph of its serialization version.
     * If the serialization version is the one used in production, the root pointer file is rewritten, which
     * redeploys the OTP instances even if the graph was already current (for example after a manual restore of the
     * stable copy), and the report index is updated. Another serialization version is rolled out without touching
     * production.
     *
     * @param pin whether to pin the graph. Otherwise a pin on the serialization version is removed.
     */
    public OtpGraphRolloutResult rollout(String graph, boolean pin, String reason, String username) {
        Otp2GraphPaths.VersionedGraph versionedGraph = Otp2GraphPaths.parseVersionedGraph(graph)
                .orElseThrow(() -> GraphControlException.badRequest("Not a published graph: " + graph
                        + ". Expected " + OTP2_NETEX_GRAPH_DIR + "/<serialization id>/<timestamp>-Graph-otp2-<serialization id>.obj"));
        String validReason = validReason(reason);
        if (!Boolean.TRUE.equals(otpGraphsBlobStoreService.blobExists(graph))) {
            throw GraphControlException.notFound("Graph not found: " + graph);
        }
        String serializationId = versionedGraph.serializationId();

        return transactionTemplate.execute(status -> {
            repository.lockControlRow(LockWait.OPERATION);
            boolean production = productionSerializationId(stateStore.readRootPointer()).filter(serializationId::equals).isPresent();

            // the pin is written first: a failure after it leaves the builds blocked, never a stale graph unpinned
            if (pin) {
                stateStore.writePin(serializationId, new GraphPin(graph, Instant.now(), username, validReason));
            } else {
                stateStore.deletePin(serializationId);
            }
            // a rollout does not change the nature of the serialization version: a candidate build keeps being
            // promoted in a version that only had candidate builds
            boolean candidate = stateStore.readPointer(serializationId).map(GraphPointer::candidate).orElse(false);
            makeCurrent(graph, serializationId, candidate, versionedGraph.reportVersion(), username, validReason, production);

            audit(pin ? ACTION_ROLLOUT_AND_PIN : ACTION_ROLLOUT, serializationId, graph, username, validReason);
            LOGGER.info("User {} rolled out the OTP2 graph {} (pinned: {}, production: {}): {}", username, graph, pin, production, validReason);
            return new OtpGraphRolloutResult(graph, serializationId, production, pin);
        });
    }

    /**
     * Pin the current graph of a serialization version, so that new builds do not replace it.
     * Unlike {@link #rollout(String, boolean, String, String)}, the graph files are unchanged and OTP is not redeployed.
     */
    public OtpGraphControlStatus pin(String serializationId, String reason, String username) {
        validSerializationId(serializationId);
        String validReason = validReason(reason);
        transactionTemplate.executeWithoutResult(status -> {
            repository.lockControlRow(LockWait.OPERATION);
            String currentGraph = stateStore.readPointer(serializationId)
                    .orElseThrow(() -> GraphControlException.notFound("No current graph for the serialization version " + serializationId))
                    .graph();
            stateStore.writePin(serializationId, new GraphPin(currentGraph, Instant.now(), username, validReason));
            audit(ACTION_PIN, serializationId, currentGraph, username, validReason);
            LOGGER.info("User {} pinned the current OTP2 graph {}: {}", username, currentGraph, validReason);
        });
        return status();
    }

    /**
     * Remove the pin of a serialization version. The current graph is unchanged: the next graph build replaces it.
     */
    public OtpGraphControlStatus unpin(String serializationId, String reason, String username) {
        validSerializationId(serializationId);
        String validReason = validReason(reason);
        transactionTemplate.executeWithoutResult(status -> {
            repository.lockControlRow(LockWait.OPERATION);
            GraphPin pin = stateStore.readPin(serializationId)
                    .orElseThrow(() -> GraphControlException.notFound("No pinned graph for the serialization version " + serializationId));
            stateStore.deletePin(serializationId);
            audit(ACTION_UNPIN, serializationId, pin.graph(), username, validReason);
            LOGGER.info("User {} unpinned the OTP2 graph {}: {}", username, pin.graph(), validReason);
        });
        return status();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Status
    // ------------------------------------------------------------------------------------------------------------

    public OtpGraphControlStatus status() {
        BucketState state = readBucketState();

        OtpGraphControlStatus.Production production = state.productionSerializationId()
                .map(id -> new OtpGraphControlStatus.Production(id, state.currentGraph(id), state.pins().containsKey(id)))
                .orElse(null);

        List<OtpGraphControlStatus.Pin> pins = state.pins().entrySet().stream()
                .map(e -> new OtpGraphControlStatus.Pin(e.getKey(), e.getValue().graph(), e.getValue().pinnedBy(), e.getValue().pinnedAt(),
                        e.getValue().reason(), state.currentGraph(e.getKey())))
                .toList();

        List<OtpGraphControlStatus.Action> recentActions = actions(repository.findLatestAuditEntriesExcluding(ACTION_BUILD_NOT_PROMOTED, RECENT_ACTIONS));
        List<OtpGraphControlStatus.Action> skippedBuilds = actions(repository.findLatestAuditEntries(ACTION_BUILD_NOT_PROMOTED, RECENT_SKIPPED_BUILDS));

        return new OtpGraphControlStatus(production, pins, recentActions, skippedBuilds);
    }

    /**
     * The published graphs, grouped by serialization version: the production serialization version first, then the
     * most recent serialization versions.
     *
     * @param limit the maximum number of graphs listed per serialization version
     */
    public OtpGraphVersions versions(int limit) {
        if (limit < 1) {
            throw GraphControlException.badRequest("The limit must be positive");
        }
        BucketState state = readBucketState();
        Map<String, List<BlobStoreFiles.File>> filesBySerializationId = new TreeMap<>(Comparator.reverseOrder());
        Map<String, Otp2GraphPaths.VersionedGraph> graphs = new LinkedHashMap<>();
        for (BlobStoreFiles.File file : state.files().getFiles()) {
            Otp2GraphPaths.parseVersionedGraph(file.getName()).ifPresent(graph -> {
                graphs.put(file.getName(), graph);
                filesBySerializationId.computeIfAbsent(graph.serializationId(), id -> new ArrayList<>()).add(file);
            });
        }
        String productionSerializationId = state.productionSerializationId().orElse(null);

        List<OtpGraphVersions.SerializationVersion> serializationVersions = new ArrayList<>();
        filesBySerializationId.forEach((serializationId, serializationIdFiles) -> {
            String currentGraph = state.currentGraph(serializationId);
            String pinnedGraph = Optional.ofNullable(state.pins().get(serializationId)).map(GraphPin::graph).orElse(null);
            List<OtpGraphVersions.Graph> latestGraphs = serializationIdFiles.stream()
                    .sorted(Comparator.comparing((BlobStoreFiles.File f) -> graphs.get(f.getName()).timestamp()).reversed())
                    .limit(limit)
                    .map(f -> {
                        Otp2GraphPaths.VersionedGraph graph = graphs.get(f.getName());
                        return new OtpGraphVersions.Graph(f.getName(), graph.fileName(), graph.timestamp(), f.getCreated(), f.getFileSize(),
                                f.getName().equals(currentGraph), f.getName().equals(pinnedGraph));
                    })
                    .toList();
            serializationVersions.add(new OtpGraphVersions.SerializationVersion(serializationId,
                    serializationId.equals(productionSerializationId), currentGraph, pinnedGraph, serializationIdFiles.size(), latestGraphs));
        });
        serializationVersions.sort(Comparator.comparing((OtpGraphVersions.SerializationVersion v) -> !v.production()));
        return new OtpGraphVersions(serializationVersions);
    }

    // ------------------------------------------------------------------------------------------------------------

    /**
     * The state of the publication read from the bucket, without the lock: a snapshot for the status.
     *
     * @param files                     the listing of the graph folder
     * @param pointers                  the pointer of each serialization version that has one
     * @param pins                      the pin of each serialization version that has one
     * @param productionSerializationId the serialization version the root pointer points at
     */
    private record BucketState(BlobStoreFiles files,
                               Map<String, GraphPointer> pointers,
                               Map<String, GraphPin> pins,
                               Optional<String> productionSerializationId) {

        String currentGraph(String serializationId) {
            GraphPointer pointer = pointers.get(serializationId);
            return pointer == null ? null : pointer.graph();
        }
    }

    private BucketState readBucketState() {
        BlobStoreFiles files = otpGraphsBlobStoreService.listBlobsInFolder(OTP2_NETEX_GRAPH_DIR);
        TreeSet<String> names = new TreeSet<>();
        TreeSet<String> serializationIds = new TreeSet<>();
        for (BlobStoreFiles.File file : files.getFiles()) {
            names.add(file.getName());
            String[] segments = file.getName().split("/");
            if (segments.length == 3 && !segments[1].isEmpty()) {
                serializationIds.add(segments[1]);
            }
        }
        Map<String, GraphPointer> pointers = new HashMap<>();
        Map<String, GraphPin> pins = new TreeMap<>();
        for (String serializationId : serializationIds) {
            if (names.contains(stateStore.versionedPointerPath(serializationId))) {
                stateStore.readPointer(serializationId).ifPresent(pointer -> pointers.put(serializationId, pointer));
            }
            if (names.contains(Otp2GraphPaths.pinPath(serializationId))) {
                stateStore.readPin(serializationId).ifPresent(pin -> pins.put(serializationId, pin));
            }
        }
        return new BucketState(files, pointers, pins, productionSerializationId(stateStore.readRootPointer()));
    }

    /**
     * The serialization version used in production is the one the root pointer file points at.
     */
    private static Optional<String> productionSerializationId(Optional<GraphPointer> rootPointer) {
        return rootPointer.flatMap(pointer -> Otp2GraphPaths.parseVersionedGraph(pointer.graph()))
                .map(Otp2GraphPaths.VersionedGraph::serializationId);
    }

    /**
     * Make a graph the current graph of its serialization version: copy it to the stable name loaded by the OTP
     * instances, then record it in the pointer file. The stable copy is the only step that can take time, and is
     * atomic: a failure before the pointer is written leaves the previous pointer in place, and running the same
     * operation again completes it. For the serialization version used in production, the report index is updated
     * and the root pointer file, which triggers the redeployment of the OTP instances, is written last.
     */
    private void makeCurrent(String graphPath, String serializationId, boolean candidate, String reportVersion, String username, String reason, boolean production) {
        otpGraphsBlobStoreService.copyBlobInBucket(graphPath, Otp2GraphPaths.stableGraphPath(serializationId));
        if (production) {
            updateReportIndex(reportVersion);
        }
        GraphPointer pointer = new GraphPointer(graphPath, candidate, Instant.now(), username, reason);
        stateStore.writePointer(serializationId, pointer);
        if (production) {
            stateStore.writeRootPointer(pointer);
        }
        LOGGER.info("The OTP2 graph {} is the current graph of the serialization version {} (production: {})", graphPath, serializationId, production);
    }

    /**
     * Point the report index at the build report of the current production graph, if the report exists: candidate
     * builds do not publish a report.
     */
    private void updateReportIndex(String reportVersion) {
        if (otpReportBlobStoreService.listBlobsInFolder(reportVersion).getFiles().isEmpty()) {
            LOGGER.warn("No build report found in {}, the report index is not updated", reportVersion);
            return;
        }
        otpReportBlobStoreService.uploadHtmlBlob(OTP2_GRAPH_REPORT_INDEX_FILE,
                IOUtils.toInputStream(Otp2GraphPaths.reportRedirectPage(otpReportContainerName, reportVersion), StandardCharsets.UTF_8));
    }

    private static List<OtpGraphControlStatus.Action> actions(List<AuditEntry> entries) {
        return entries.stream()
                .map(a -> new OtpGraphControlStatus.Action(a.action(), a.serializationId(), a.graphFile(), a.performedBy(), a.performedAt(), a.reason()))
                .toList();
    }

    private void audit(String action, String serializationId, String graphFile, String username, String reason) {
        Instant now = Instant.now();
        if (ACTION_BUILD_NOT_PROMOTED.equals(action)) {
            repository.deleteAuditEntriesBefore(ACTION_BUILD_NOT_PROMOTED, now.minus(SKIPPED_BUILD_RETENTION));
        }
        repository.insertAuditEntry(new AuditEntry(action, serializationId, graphFile,
                username == null ? SYSTEM_USER : username, now, reason));
    }

    private static void validSerializationId(String serializationId) {
        if (serializationId == null || serializationId.isBlank()) {
            throw GraphControlException.badRequest("Missing serialization id");
        }
    }

    private static String validReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw GraphControlException.badRequest("A reason is required");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw GraphControlException.badRequest("The reason must not exceed " + MAX_REASON_LENGTH + " characters");
        }
        return reason.trim();
    }
}
