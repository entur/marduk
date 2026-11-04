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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.domain.OtpGraphControlStatus;
import no.rutebanken.marduk.domain.OtpGraphRolloutResult;
import no.rutebanken.marduk.domain.OtpGraphVersions;
import no.rutebanken.marduk.exceptions.GraphControlException;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.graph.GraphControlRepository.AuditEntry;
import no.rutebanken.marduk.graph.GraphControlRepository.ControlState;
import no.rutebanken.marduk.graph.GraphControlRepository.CurrentGraph;
import no.rutebanken.marduk.graph.GraphControlRepository.DeferredBuildRequest;
import no.rutebanken.marduk.graph.GraphControlRepository.GraphPin;
import no.rutebanken.marduk.graph.GraphControlRepository.GraphType;
import no.rutebanken.marduk.graph.GraphControlRepository.LockWait;
import no.rutebanken.marduk.graph.GraphControlRepository.PauseState;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.services.OtpGraphsBlobStoreService;
import no.rutebanken.marduk.services.OtpReportBlobStoreService;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.OTP2_GRAPH_REPORT_INDEX_FILE;
import static no.rutebanken.marduk.Constants.OTP2_NETEX_GRAPH_DIR;

/**
 * Emergency controls of the OTP2 transit graph publication:
 * <ul>
 *     <li>roll out a published graph, that is make it the current graph of its serialization version (the stable copy
 *     loaded by the OTP instances), and optionally pin it so that new builds do not replace it;</li>
 *     <li>pause the graph builder: the build requests are stored and replayed when the graph builder is resumed, and a
 *     graph whose build was already running is published but not promoted.</li>
 * </ul>
 * The state of the publication is kept in the database: the current graph of each serialization version, and the
 * serialization version used in production, which is the serialization version of the latest graph built by the
 * production (non-candidate) graph builder, since the graph builder runs the OTP version deployed in production.
 * The pointer files of the graph bucket are only written, for the OTP redeployment job which restarts the OTP
 * instances when the root pointer file changes; nothing reads them back, so they can be dropped together with that
 * job.
 * <p>
 * The promotion of a new graph by the graph builder and the control operations lock the same database row, so that
 * they are applied one at a time across the cluster.
 */
@Service
public class Otp2GraphControlService {

    public enum Promotion {
        PROMOTED, SKIPPED_PINNED, SKIPPED_PAUSED, SKIPPED_OLDER, SKIPPED_CANDIDATE, FAILED
    }

    /**
     * The route republishing a deferred build request to the graph build queue matching the graph type stored in the
     * exchange property {@link #PROP_GRAPH_TYPE}.
     */
    public static final String REPUBLISH_BUILD_REQUEST_ENDPOINT = "direct:republishOtp2GraphBuildRequest";
    public static final String PROP_GRAPH_TYPE = "RutebankenOtpGraphType";

    static final String ACTION_ROLLOUT = "ROLLOUT";
    static final String ACTION_ROLLOUT_AND_PIN = "ROLLOUT_AND_PIN";
    static final String ACTION_PIN = "PIN";
    static final String ACTION_UNPIN = "UNPIN";
    static final String ACTION_PAUSE = "PAUSE";
    static final String ACTION_RESUME = "RESUME";
    static final String ACTION_BUILD_NOT_PROMOTED = "BUILD_NOT_PROMOTED";
    static final String ACTION_PRODUCTION_VERSION_CHANGED = "PRODUCTION_VERSION_CHANGED";

    static final String SYSTEM_USER = "marduk";

    private static final int MAX_REASON_LENGTH = 1000;
    private static final int RECENT_ACTIONS = 20;
    private static final int RECENT_SKIPPED_BUILDS = 10;
    /**
     * Builds that are not promoted are audited at every provider publication while a pin is set: they are kept for
     * a limited time, unlike the control operations.
     */
    private static final Duration SKIPPED_BUILD_RETENTION = Duration.ofDays(30);
    private static final TypeReference<Map<String, String>> ATTRIBUTES_TYPE = new TypeReference<>() {
    };

    private static final Logger LOGGER = LoggerFactory.getLogger(Otp2GraphControlService.class);

    private final GraphControlRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final OtpGraphsBlobStoreService otpGraphsBlobStoreService;
    private final OtpReportBlobStoreService otpReportBlobStoreService;
    private final ObjectProvider<ProducerTemplate> producerTemplateProvider;
    private final String currentGraphPointerFile;
    private final String redeployLastFile;
    private final String otpReportContainerName;

    public Otp2GraphControlService(GraphControlRepository repository,
                                   PlatformTransactionManager transactionManager,
                                   OtpGraphsBlobStoreService otpGraphsBlobStoreService,
                                   OtpReportBlobStoreService otpReportBlobStoreService,
                                   ObjectProvider<ProducerTemplate> producerTemplateProvider,
                                   @Value("${otp2.graph.current.file:current-otp2}") String currentGraphPointerFile,
                                   @Value("${otp2.graph.redeploy.last.file:last-otp2}") String redeployLastFile,
                                   @Value("${blobstore.gcs.otpreport.container.name}") String otpReportContainerName) {
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.otpGraphsBlobStoreService = otpGraphsBlobStoreService;
        this.otpReportBlobStoreService = otpReportBlobStoreService;
        this.producerTemplateProvider = producerTemplateProvider;
        this.currentGraphPointerFile = currentGraphPointerFile;
        this.redeployLastFile = redeployLastFile;
        this.otpReportContainerName = otpReportContainerName;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Graph builder hooks
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Store the build requests if the graph builder is paused, so that they are replayed when it is resumed.
     * Candidate builds are not affected by the pause.
     *
     * @param graphType the type of graph requested
     * @param candidate whether the graph is built by a candidate version of OTP
     * @param messages  the aggregated build requests
     * @return true if the graph builder is paused and the build must not start
     */
    public boolean deferIfPaused(GraphType graphType, boolean candidate, List<Message> messages) {
        if (candidate) {
            return false;
        }
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            if (!repository.lockControlRow(LockWait.BUILD).paused()) {
                return false;
            }
            Instant now = Instant.now();
            List<Message> requests = messages == null ? List.of() : messages;
            if (requests.isEmpty()) {
                repository.insertDeferredBuildRequest(graphType, null, "{}", now);
            }
            for (Message message : requests) {
                String messageId = message.getHeader(GooglePubsubConstants.MESSAGE_ID, String.class);
                // a redelivered message is stored only once
                if (messageId == null || !repository.deferredBuildRequestExists(messageId)) {
                    repository.insertDeferredBuildRequest(graphType, messageId, toJson(republishedAttributes(message)), now);
                }
            }
            LOGGER.info("The graph builder is paused: deferred {} {} graph build request(s)", Math.max(1, requests.size()), graphType);
            return true;
        }));
    }

    /**
     * Do not publish a street graph whose build was already running when the graph builder was paused: the street
     * graph is used by every later transit graph build, and there is no rollout for it. The street graph is rebuilt
     * when the graph builder is resumed.
     *
     * @param graphPath the path of the built street graph
     * @return true if the graph builder is paused and the street graph must not be published
     */
    public boolean deferBuiltStreetGraphIfPaused(String graphPath) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            if (!repository.lockControlRow(LockWait.BUILD).paused()) {
                return false;
            }
            repository.insertDeferredBuildRequest(GraphType.BASE, null, "{}", Instant.now());
            audit(ACTION_BUILD_NOT_PROMOTED, null, graphPath, SYSTEM_USER, "The graph builder is paused");
            LOGGER.info("The graph builder is paused: the street graph {} is not published and is rebuilt on resume", graphPath);
            return true;
        }));
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

    /**
     * Promote a graph published by the graph builder, that is make it the current graph of its serialization version,
     * unless the serialization version is pinned, the graph builder is paused or a more recent graph is already current.
     * <p>
     * A production build defines the serialization version used in production. For such a build, the root pointer
     * file, which triggers the redeployment of the OTP instances, and the report index are updated. A candidate build
     * of the production serialization version is never promoted, so that the stable copy loaded by the production OTP
     * instances only ever contains production builds. A candidate build of another serialization version is promoted
     * even while the graph builder is paused, since production is not affected.
     *
     * @param graphPath       the path of the published graph
     * @param serializationId the serialization version of the graph
     * @param candidate       whether the graph was built by a candidate version of OTP
     * @param reportVersion   the folder of the build report in the report bucket
     */
    public Promotion promoteBuiltGraph(String graphPath, String serializationId, boolean candidate, String reportVersion) {
        return transactionTemplate.execute(status -> {
            ControlState control = repository.lockControlRow(LockWait.BUILD);
            String productionSerializationId = control.productionSerializationId();
            if (!candidate && !serializationId.equals(productionSerializationId)) {
                // the production graph builder runs the OTP version deployed in production
                repository.updateProductionSerializationId(serializationId);
                String change = productionSerializationId == null
                        ? "The production serialization version is " + serializationId
                        : "The production serialization version changed from " + productionSerializationId + " to " + serializationId;
                audit(ACTION_PRODUCTION_VERSION_CHANGED, serializationId, graphPath, SYSTEM_USER, change);
                LOGGER.info(change);
                productionSerializationId = serializationId;
            }
            boolean production = serializationId.equals(productionSerializationId);

            if (candidate && production) {
                audit(ACTION_BUILD_NOT_PROMOTED, serializationId, graphPath, SYSTEM_USER, "Candidate build of the production serialization version");
                return Promotion.SKIPPED_CANDIDATE;
            }

            Optional<GraphPin> pin = repository.findPin(serializationId);
            if (pin.isPresent()) {
                audit(ACTION_BUILD_NOT_PROMOTED, serializationId, graphPath, SYSTEM_USER, "The serialization version is pinned to " + pin.get().graphFile());
                return Promotion.SKIPPED_PINNED;
            }

            if (control.paused() && !candidate) {
                // the build ran for requests that are not served yet: rebuild when the graph builder is resumed
                repository.insertDeferredBuildRequest(GraphType.TRANSIT, null, "{}", Instant.now());
                audit(ACTION_BUILD_NOT_PROMOTED, serializationId, graphPath, SYSTEM_USER, "The graph builder is paused");
                return Promotion.SKIPPED_PAUSED;
            }

            Optional<CurrentGraph> currentGraph = repository.findCurrentGraph(serializationId);
            // a production build is not held back by an overlapping candidate build of the same serialization version
            if (currentGraph.isPresent() && (candidate || !currentGraph.get().candidate())
                    && Otp2GraphPaths.isBuiltBefore(graphPath, currentGraph.get().graphFile())) {
                audit(ACTION_BUILD_NOT_PROMOTED, serializationId, graphPath, SYSTEM_USER, "A more recent graph is current: " + currentGraph.get().graphFile());
                return Promotion.SKIPPED_OLDER;
            }

            makeCurrent(graphPath, serializationId, production, candidate, reportVersion, SYSTEM_USER);
            return Promotion.PROMOTED;
        });
    }

    // ------------------------------------------------------------------------------------------------------------
    // Control operations
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Make a published graph the current graph of its serialization version.
     * If the graph belongs to the serialization version used in production, the root pointer file is updated and the
     * state file of the redeployment job is deleted, so that the OTP instances are redeployed even if the root pointer
     * file already referenced the graph (for example after a manual rollback of the stable copy). Another
     * serialization version is rolled out without touching production.
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
            ControlState control = repository.lockControlRow(LockWait.OPERATION);
            boolean production = serializationId.equals(control.productionSerializationId());

            makeCurrent(graph, serializationId, production, false, versionedGraph.reportVersion(), username);
            if (production) {
                // the redeployment job restarts OTP when the root pointer file differs from its last known content, or
                // when the last known content is missing
                otpGraphsBlobStoreService.deleteBlob(redeployLastFile);
                LOGGER.info("Deleted {} to force the redeployment of OTP", redeployLastFile);
            }

            if (pin) {
                repository.savePin(new GraphPin(serializationId, graph, username, Instant.now(), validReason));
            } else {
                repository.deletePin(serializationId);
            }
            audit(pin ? ACTION_ROLLOUT_AND_PIN : ACTION_ROLLOUT, serializationId, graph, username, validReason);
            LOGGER.info("User {} rolled out the OTP2 graph {} (pinned: {}, production: {}): {}", username, graph, pin, production, validReason);
            return new OtpGraphRolloutResult(graph, serializationId, production, production, pin);
        });
    }

    /**
     * Pin the current graph of a serialization version, so that new builds do not replace it.
     * Unlike {@link #rollout(String, boolean, String, String)}, the graph files are unchanged and OTP is not redeployed.
     */
    public OtpGraphControlStatus pin(String serializationId, String reason, String username) {
        if (serializationId == null || serializationId.isBlank()) {
            throw GraphControlException.badRequest("Missing serialization id");
        }
        String validReason = validReason(reason);
        transactionTemplate.executeWithoutResult(status -> {
            repository.lockControlRow(LockWait.OPERATION);
            String currentGraph = repository.findCurrentGraph(serializationId)
                    .orElseThrow(() -> GraphControlException.notFound("No current graph for the serialization version " + serializationId))
                    .graphFile();
            repository.savePin(new GraphPin(serializationId, currentGraph, username, Instant.now(), validReason));
            audit(ACTION_PIN, serializationId, currentGraph, username, validReason);
            LOGGER.info("User {} pinned the current OTP2 graph {}: {}", username, currentGraph, validReason);
        });
        return status();
    }

    /**
     * Remove the pin of a serialization version. The current graph is unchanged: the next graph build replaces it.
     */
    public OtpGraphControlStatus unpin(String serializationId, String reason, String username) {
        if (serializationId == null || serializationId.isBlank()) {
            throw GraphControlException.badRequest("Missing serialization id");
        }
        String validReason = validReason(reason);
        transactionTemplate.executeWithoutResult(status -> {
            repository.lockControlRow(LockWait.OPERATION);
            GraphPin pin = repository.findPin(serializationId)
                    .orElseThrow(() -> GraphControlException.notFound("No pinned graph for the serialization version " + serializationId));
            repository.deletePin(serializationId);
            audit(ACTION_UNPIN, serializationId, pin.graphFile(), username, validReason);
            LOGGER.info("User {} unpinned the OTP2 graph {}: {}", username, pin.graphFile(), validReason);
        });
        return status();
    }

    /**
     * Pause the graph builder: new build requests are stored, and a graph whose build is running is not promoted.
     */
    public OtpGraphControlStatus pause(String reason, String username) {
        String validReason = validReason(reason);
        transactionTemplate.executeWithoutResult(status -> {
            if (repository.lockControlRow(LockWait.OPERATION).paused()) {
                LOGGER.info("User {} requested to pause the graph builder, which is already paused", username);
                return;
            }
            repository.updatePauseState(new PauseState(true, username, Instant.now(), validReason));
            audit(ACTION_PAUSE, null, null, username, validReason);
            LOGGER.info("User {} paused the OTP graph builder: {}", username, validReason);
        });
        return status();
    }

    /**
     * Resume the graph builder and replay the build requests received while it was paused.
     * The replayed requests are aggregated into one build per graph type by the build routes.
     * If a request cannot be replayed, the graph builder stays paused.
     */
    public OtpGraphControlStatus resume(String reason, String username) {
        String validReason = validReason(reason);
        transactionTemplate.executeWithoutResult(status -> {
            if (!repository.lockControlRow(LockWait.OPERATION).paused()) {
                LOGGER.info("User {} requested to resume the graph builder, which is not paused", username);
                return;
            }
            repository.updatePauseState(new PauseState(false, null, null, null));
            List<DeferredBuildRequest> deferredBuildRequests = repository.findDeferredBuildRequests();
            for (DeferredBuildRequest request : deferredBuildRequests) {
                republish(request);
                repository.deleteDeferredBuildRequest(request.id());
            }
            audit(ACTION_RESUME, null, null, username, validReason + " (" + deferredBuildRequests.size() + " deferred build request(s) replayed)");
            LOGGER.info("User {} resumed the OTP graph builder, replayed {} deferred build request(s): {}", username, deferredBuildRequests.size(), validReason);
        });
        return status();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Status
    // ------------------------------------------------------------------------------------------------------------

    public OtpGraphControlStatus status() {
        return transactionTemplate.execute(status -> {
            ControlState control = repository.getControlState();
            OtpGraphControlStatus.Builder builder = new OtpGraphControlStatus.Builder(
                    control.paused(),
                    control.pausedBy(),
                    control.pausedAt(),
                    control.pauseReason(),
                    repository.countDeferredBuildRequests(GraphType.TRANSIT),
                    repository.countDeferredBuildRequests(GraphType.BASE));

            List<GraphPin> pins = repository.findPins();
            Map<String, String> currentGraphs = currentGraphs();

            String productionSerializationId = control.productionSerializationId();
            OtpGraphControlStatus.Production production = null;
            if (productionSerializationId != null) {
                boolean pinned = pins.stream().anyMatch(p -> p.serializationId().equals(productionSerializationId));
                production = new OtpGraphControlStatus.Production(productionSerializationId, currentGraphs.get(productionSerializationId), pinned);
            }

            List<OtpGraphControlStatus.Pin> pinStatus = pins.stream()
                    .map(p -> new OtpGraphControlStatus.Pin(p.serializationId(), p.graphFile(), p.pinnedBy(), p.pinnedAt(), p.reason(), currentGraphs.get(p.serializationId())))
                    .toList();

            List<OtpGraphControlStatus.Action> recentActions = actions(repository.findLatestAuditEntriesExcluding(ACTION_BUILD_NOT_PROMOTED, RECENT_ACTIONS));
            List<OtpGraphControlStatus.Action> skippedBuilds = actions(repository.findLatestAuditEntries(ACTION_BUILD_NOT_PROMOTED, RECENT_SKIPPED_BUILDS));

            return new OtpGraphControlStatus(builder, production, pinStatus, recentActions, skippedBuilds);
        });
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
        BlobStoreFiles files = otpGraphsBlobStoreService.listBlobsInFolder(OTP2_NETEX_GRAPH_DIR);
        Map<String, List<BlobStoreFiles.File>> filesBySerializationId = new TreeMap<>(Comparator.reverseOrder());
        Map<String, Otp2GraphPaths.VersionedGraph> graphs = new LinkedHashMap<>();
        for (BlobStoreFiles.File file : files.getFiles()) {
            Otp2GraphPaths.parseVersionedGraph(file.getName()).ifPresent(graph -> {
                graphs.put(file.getName(), graph);
                filesBySerializationId.computeIfAbsent(graph.serializationId(), id -> new ArrayList<>()).add(file);
            });
        }

        record PublicationState(String productionSerializationId, Map<String, String> currentGraphs, Map<String, String> pinnedGraphs) {
        }
        PublicationState state = transactionTemplate.execute(status -> new PublicationState(
                repository.getControlState().productionSerializationId(),
                currentGraphs(),
                repository.findPins().stream().collect(Collectors.toMap(GraphPin::serializationId, GraphPin::graphFile))));
        String productionSerializationId = state.productionSerializationId();

        List<OtpGraphVersions.SerializationVersion> serializationVersions = new ArrayList<>();
        filesBySerializationId.forEach((serializationId, serializationIdFiles) -> {
            String currentGraph = state.currentGraphs().get(serializationId);
            String pinnedGraph = state.pinnedGraphs().get(serializationId);
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
     * The current graph of each serialization version, by serialization version. Must be called within a transaction.
     */
    private Map<String, String> currentGraphs() {
        return repository.findCurrentGraphs().stream().collect(Collectors.toMap(CurrentGraph::serializationId, CurrentGraph::graphFile));
    }

    /**
     * Make a graph the current graph of its serialization version: record it in the database, copy it to the stable
     * name loaded by the OTP instances, then write the pointer files. The stable copy must be in place before the root
     * pointer file triggers the redeployment of the OTP instances. The database record is rolled back if a file
     * operation fails.
     */
    private void makeCurrent(String graphPath, String serializationId, boolean production, boolean candidate, String reportVersion, String username) {
        repository.saveCurrentGraph(new CurrentGraph(serializationId, graphPath, candidate, username, Instant.now()));
        otpGraphsBlobStoreService.copyBlobInBucket(graphPath, Otp2GraphPaths.stableGraphPath(serializationId));
        // the pointer files are written for the OTP redeployment job only, they are not read back
        uploadPointer(Otp2GraphPaths.versionedPointerPath(serializationId, currentGraphPointerFile), graphPath);
        if (production) {
            uploadPointer(currentGraphPointerFile, graphPath);
            updateReportIndex(reportVersion);
        }
        LOGGER.info("The OTP2 graph {} is the current graph of the serialization version {} (production: {})", graphPath, serializationId, production);
    }

    private void uploadPointer(String pointerPath, String graphPath) {
        otpGraphsBlobStoreService.uploadBlobWithoutVersionHeader(pointerPath, new ByteArrayInputStream(graphPath.getBytes(StandardCharsets.UTF_8)));
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

    private void republish(DeferredBuildRequest request) {
        Map<String, String> attributes = new TreeMap<>(fromJson(request.attributes()));
        // PubSub rejects a message without data and without attributes
        attributes.putIfAbsent(Constants.CORRELATION_ID, UUID.randomUUID().toString());
        Exchange result = producerTemplateProvider.getObject().send(REPUBLISH_BUILD_REQUEST_ENDPOINT, exchange -> {
            exchange.getIn().setBody("");
            attributes.forEach((name, value) -> exchange.getIn().setHeader(name, value));
            exchange.setProperty(PROP_GRAPH_TYPE, request.graphType());
        });
        if (result.getException() != null) {
            throw new MardukException("Failed to replay the deferred graph build request " + request.id(), result.getException());
        }
    }

    /**
     * The headers of a build request that are published as PubSub attributes, following the rules of the outbound
     * PubSub interceptor of the route builders. The acknowledgement holder of the aggregated message is an exchange,
     * not an attribute.
     */
    private static Map<String, String> republishedAttributes(Message message) {
        Map<String, Object> headers = new TreeMap<>(message.getHeaders());
        headers.remove(BaseRouteBuilder.SYNCHRONIZATION_HOLDER);
        return new TreeMap<>(BaseRouteBuilder.pubSubAttributes(headers));
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

    private static String validReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw GraphControlException.badRequest("A reason is required");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw GraphControlException.badRequest("The reason must not exceed " + MAX_REASON_LENGTH + " characters");
        }
        return reason.trim();
    }

    private static String toJson(Map<String, String> attributes) {
        try {
            return ObjectMapperFactory.getSharedObjectMapper().writeValueAsString(attributes);
        } catch (JsonProcessingException e) {
            throw new MardukException("Failed to serialize the build request attributes", e);
        }
    }

    private static Map<String, String> fromJson(String attributes) {
        try {
            return ObjectMapperFactory.getSharedObjectMapper().readValue(attributes, ATTRIBUTES_TYPE);
        } catch (JsonProcessingException e) {
            throw new MardukException("Failed to deserialize the build request attributes", e);
        }
    }
}
