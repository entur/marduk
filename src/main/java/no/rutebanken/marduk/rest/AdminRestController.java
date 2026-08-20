package no.rutebanken.marduk.rest;

import jakarta.ws.rs.NotFoundException;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.chouette.ChouetteJobCleanup;
import no.rutebanken.marduk.chouette.ChouetteJobs;
import no.rutebanken.marduk.chouette.ChouetteValidationTriggers;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.domain.OtpGraphsInfo;
import no.rutebanken.marduk.osm.OsmMapFetcher;
import no.rutebanken.marduk.otp.OtpGraphs;
import no.rutebanken.marduk.pipeline.MardukMdc;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.rest.openapi.model.UploadResult;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.security.MardukAuthorizationService;
import no.rutebanken.marduk.security.UsernameService;
import no.rutebanken.marduk.services.IdempotentRepositoryService;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import no.rutebanken.marduk.services.MardukPublicBlobStoreService;
import no.rutebanken.marduk.upload.TimetableFileUploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.IMPORT_TYPE_NETEX_FLEX;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.PROVIDER_IDS;
import static no.rutebanken.marduk.Constants.USERNAME;

/**
 * The admin API, used by the operator front ends (Ninkasi, Bel).
 *
 * <p>Replaces {@code AdminRestRouteBuilder}: the REST DSL, the {@code platform-http} component, and the
 * {@code direct:} route per endpoint that existed only to be a REST target. Paths, methods, status codes and
 * content types are unchanged - Ninkasi is not being asked to move.
 *
 * <p>Three things the routes needed and Spring does not:
 *
 * <ul>
 *   <li><b>Header stripping.</b> Every route began with {@code removeHttpHeaders} because platform-http
 *       copied the request headers, {@code Authorization} included, onto the exchange. A message built here
 *       carries only what is put on it.
 *   <li><b>Authorization off the request thread.</b> platform-http handed the exchange to a worker thread
 *       without a {@code SecurityContext}, so the authorization service rebuilt one from the bearer token.
 *       A controller method runs on the request thread and the context is simply there.
 *   <li><b>The wildcard {@code rest("")} routes.</b> They existed to make Jetty match authorization filters
 *       against paths with path parameters. Spring Security matches the mappings directly.
 * </ul>
 *
 * <p>Two deliberate deviations, both uniform where the routes were not. Every action now carries a
 * correlation id, so its job events can be followed in nabu - the routes only set one on about half of
 * them. And authorization is checked before the provider is looked up everywhere, where two routes did it
 * the other way round and told an unauthorized caller whether a provider id exists.
 *
 * <p>The three {@code line_statistics} endpoints are gone. Their {@code direct:chouetteGetStats*} consumers
 * were deleted with Chouette statistics support, leaving {@code .to()} calls that could only fail; every
 * request to them has returned a 500 since. See {@link AdminExternalRestController} for the machine-to-machine
 * API.
 */
@RestController
public class AdminRestController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminRestController.class);


    /** Not {@code application/octet-stream}: the REST DSL declared this spelling and clients match on it. */
    private static final String X_OCTET_STREAM = "application/x-octet-stream";

    private static final String OTP2_BASE_GRAPH_TYPE = "otp2_base";
    private static final String OTP2_NETEX_GRAPH_TYPE = "otp2_netex";

    private final MardukAuthorizationService authorizationService;
    private final UsernameService usernameService;
    private final ProviderRepository providerRepository;
    private final MardukInternalBlobStoreService internalBlobStore;
    private final MardukPubSubPublisher publisher;
    private final MardukPublicBlobStoreService publicBlobStore;
    private final List<String> timetableExportPrefixes;
    private final TimetableFileUploader fileUploader;
    private final IdempotentRepositoryService idempotentRepositoryService;
    private final ChouetteJobCleanup chouetteJobCleanup;
    private final ChouetteJobs chouetteJobs;
    private final ChouetteValidationTriggers validationTriggers;
    private final OsmMapFetcher osmMapFetcher;
    private final OtpGraphs otpGraphs;
    private final boolean duplicateFilterWeb;
    private final boolean duplicateFilterRest;
    private final boolean httpImportEnabled;

    public AdminRestController(
            MardukAuthorizationService authorizationService,
            UsernameService usernameService,
            ProviderRepository providerRepository,
            MardukInternalBlobStoreService internalBlobStore,
            MardukPubSubPublisher publisher,
            MardukPublicBlobStoreService publicBlobStore,
            TimetableFileUploader fileUploader,
            IdempotentRepositoryService idempotentRepositoryService,
            ChouetteJobCleanup chouetteJobCleanup,
            ChouetteJobs chouetteJobs,
            ChouetteValidationTriggers validationTriggers,
            OsmMapFetcher osmMapFetcher,
            OtpGraphs otpGraphs,
            @Value("${duplicate.filter.web:true}") boolean duplicateFilterWeb,
            @Value("${duplicate.filter.rest:true}") boolean duplicateFilterRest,
            @Value("${netex.import.http.autoStartup:true}") boolean httpImportEnabled,
            @Value("#{'${timetable.export.blob.prefixes:outbound/gtfs/,outbound/netex/}'.split(',')}")
            List<String> timetableExportPrefixes) {
        this.authorizationService = authorizationService;
        this.usernameService = usernameService;
        this.providerRepository = providerRepository;
        this.internalBlobStore = internalBlobStore;
        this.publisher = publisher;
        this.publicBlobStore = publicBlobStore;
        this.fileUploader = fileUploader;
        this.idempotentRepositoryService = idempotentRepositoryService;
        this.chouetteJobCleanup = chouetteJobCleanup;
        this.chouetteJobs = chouetteJobs;
        this.validationTriggers = validationTriggers;
        this.osmMapFetcher = osmMapFetcher;
        this.otpGraphs = otpGraphs;
        this.duplicateFilterWeb = duplicateFilterWeb;
        this.duplicateFilterRest = duplicateFilterRest;
        this.httpImportEnabled = httpImportEnabled;
        this.timetableExportPrefixes = timetableExportPrefixes;
    }

    // ----------------------------------------------------------------------------------- all providers

    @PostMapping("/services/timetable_admin/idempotentfilter/clean")
    public ResponseEntity<String> cleanIdempotentFilter() {
        adminRequest();
        idempotentRepositoryService.cleanUniqueFileNameAndDigestRepo();
        return accepted();
    }

    @PostMapping("/services/timetable_admin/validate/prevalidation")
    public ResponseEntity<String> triggerPrevalidationForAllProviders() {
        adminRequest();
        LOGGER.info("Triggering prevalidation for all providers");
        validationTriggers.triggerAntuValidationForAllProviders();
        return accepted();
    }

    @PostMapping("/services/timetable_admin/validate/level2")
    public ResponseEntity<String> validateLevel2ForAllProviders() {
        adminRequest();
        LOGGER.info("Chouette start validation level2 for all providers");
        validationTriggers.validateLevel2ForAllProviders();
        return accepted();
    }

    @GetMapping("/services/timetable_admin/jobs")
    public List<ProviderAndJobs> listJobsForAllProviders(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String action) {
        adminRequest();
        return chouetteJobs.allJobsPerProvider(status != null ? status : List.of("STARTED", "SCHEDULED"), action);
    }

    @DeleteMapping("/services/timetable_admin/jobs")
    public ResponseEntity<String> cancelAllJobsForAllProviders() {
        adminRequest();
        LOGGER.info("Cancel all chouette jobs for all providers");
        chouetteJobs.cancelAllForAllProviders();
        return accepted();
    }

    @DeleteMapping("/services/timetable_admin/completed_jobs")
    public ResponseEntity<String> removeCompletedJobs(
            @RequestParam(required = false) Integer keepJobs,
            @RequestParam(required = false) Integer keepDays) {
        adminRequest();
        LOGGER.info("Removing old chouette jobs for all providers");
        chouetteJobCleanup.removeOldJobs(keepJobs, keepDays);
        return accepted();
    }

    @PostMapping("/services/timetable_admin/clean/{filter}")
    public ResponseEntity<String> cleanAllReferentials(@PathVariable String filter) {
        adminRequest();
        chouetteJobs.cleanAll(filter);
        return accepted();
    }

    @PostMapping("/services/timetable_admin/stop_places/clean")
    public ResponseEntity<String> cleanStopPlaces() {
        adminRequest();
        chouetteJobs.cleanStopPlaces();
        return accepted();
    }

    @GetMapping("/services/timetable_admin/export/files")
    public BlobStoreFiles listTimetableExportAndGraphFiles() {
        adminRequest();
        LOGGER.info("List time table and graph files");
        return publicBlobStore.listBlobsInFolders(timetableExportPrefixes);
    }

    @PostMapping("/services/timetable_admin/export/gtfs/merged")
    public ResponseEntity<String> exportMergedGtfs() {
        MardukMessage message = adminRequest();
        LOGGER.info("Triggered merged GTFS export");
        publisher.publish(MardukQueues.GTFS_EXPORT_MERGED_QUEUE, message);
        return accepted();
    }

    @PostMapping("/services/timetable_admin/routing_graph/build_base")
    public ResponseEntity<String> buildBaseGraph() {
        MardukMessage message = adminRequest();
        LOGGER.info("Triggered build of OTP base graph with map data");
        publisher.publish(MardukQueues.OTP2_BASE_GRAPH_BUILD_QUEUE, message);
        return accepted();
    }

    @PostMapping("/services/timetable_admin/routing_graph/build")
    public ResponseEntity<String> buildGraphFromNetex() {
        MardukMessage message = adminRequest();
        LOGGER.info("OTP build graph from NeTEx");
        publisher.publish(MardukQueues.OTP2_GRAPH_BUILD_QUEUE, message);
        return accepted();
    }

    @PostMapping("/services/timetable_admin/routing_graph/build_candidate/{graphType}")
    public ResponseEntity<String> buildCandidateGraph(@PathVariable String graphType) {
        MardukMessage message = adminRequest();
        switch (graphType) {
            case OTP2_BASE_GRAPH_TYPE -> {
                LOGGER.info("OTP2 build candidate base graph");
                publisher.publish(MardukQueues.OTP2_BASE_GRAPH_CANDIDATE_BUILD_QUEUE, message);
            }
            case OTP2_NETEX_GRAPH_TYPE -> {
                LOGGER.info("OTP2 build candidate NeTEx graph");
                publisher.publish(MardukQueues.OTP2_GRAPH_CANDIDATE_BUILD_QUEUE, message);
            }
            default -> {
                return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("Unknown Graph Type");
            }
        }
        return accepted();
    }

    @GetMapping("/services/timetable_admin/routing_graph/graphs")
    public OtpGraphsInfo listGraphs() {
        adminRequest();
        LOGGER.info("List graphs status");
        return otpGraphs.list();
    }

    // ------------------------------------------------------------------ deprecated codespace endpoints

    /** @deprecated use {@code POST /services/timetable-management/datasets/{codespace}} */
    @Deprecated(since = "the timetable-management API")
    @PostMapping(value = "/services/timetable_admin/upload/{codespace}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResult uploadByCodespace(
            @PathVariable String codespace, @RequestParam MultiValueMap<String, MultipartFile> parts) {
        requireHttpImportEnabled();
        String correlationId = newCorrelationId();
        LOGGER.info("Received file from provider {} through the HTTP endpoint", codespace);
        Long providerId = providerIdOf(codespace);
        authorizationService.verifyRouteDataEditorPrivileges(providerId);
        LOGGER.info("Authorization OK for HTTP endpoint, uploading files and starting import pipeline");

        TimetableFileUploader.Upload upload = new TimetableFileUploader.Upload(
                codespace, providerId, correlationId, usernameService.getPreferredUsername(),
                null, duplicateFilterRest, false);
        fileUploader.uploadAll(uploadedFiles(parts), upload);
        return new UploadResult().correlationId(correlationId);
    }

    /** @deprecated use {@code GET /services/timetable-management/datasets/{codespace}} */
    @Deprecated(since = "the timetable-management API")
    @GetMapping("/services/timetable_admin/download_netex_blocks/{codespace}")
    public ResponseEntity<Resource> downloadNetexBlocks(@PathVariable String codespace) {
        newCorrelationId();
        LOGGER.info("Received Blocks download request for provider {} through the HTTP endpoint", codespace);
        Long providerId = providerIdOf(codespace);
        authorizationService.verifyBlockViewerPrivileges(providerId);

        String fileHandle = Constants.BLOBSTORE_PATH_NETEX_BLOCKS_EXPORT
                + "rb_" + codespace.toLowerCase()
                + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
        LOGGER.info("Downloading NeTEx dataset with blocks: {}", fileHandle);
        return blobResponse(fileHandle);
    }

    // ------------------------------------------------------------------------------------ per provider

    @PostMapping("/services/timetable_admin/{providerId}/import")
    public ResponseEntity<String> importFiles(@PathVariable Long providerId, @RequestBody BlobStoreFiles files) {
        return startImport(providerId, files, null);
    }

    @PostMapping("/services/timetable_admin/{providerId}/flex/import")
    public ResponseEntity<String> importFlexFiles(@PathVariable Long providerId, @RequestBody BlobStoreFiles files) {
        return startImport(providerId, files, IMPORT_TYPE_NETEX_FLEX);
    }

    @GetMapping("/services/timetable_admin/{providerId}/files")
    public BlobStoreFiles listFilesForReimport(@PathVariable Long providerId) {
        adminRequest(providerId);
        LOGGER.info("List files in blob store");
        return internalBlobStore.listBlobsFlat(providerRepository.getReferential(providerId));
    }

    @PostMapping(value = "/services/timetable_admin/{providerId}/files",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadFiles(
            @PathVariable Long providerId, @RequestParam MultiValueMap<String, MultipartFile> parts) {
        return upload(providerId, parts, null);
    }

    @PostMapping(value = "/services/timetable_admin/{providerId}/flex/files",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadFlexFiles(
            @PathVariable Long providerId, @RequestParam MultiValueMap<String, MultipartFile> parts) {
        return upload(providerId, parts, IMPORT_TYPE_NETEX_FLEX);
    }

    @GetMapping("/services/timetable_admin/{providerId}/files/{fileName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long providerId, @PathVariable String fileName) {
        adminRequest(providerId);
        String fileHandle = Constants.BLOBSTORE_PATH_INBOUND
                + providerRepository.getReferential(providerId) + "/" + fileName;
        LOGGER.info("blob store download file by name {}", fileHandle);
        return blobResponse(fileHandle);
    }

    @GetMapping("/services/timetable_admin/{providerId}/jobs")
    public List<JobResponse> listJobs(
            @PathVariable Long providerId,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String action) {
        adminRequest(providerId);
        LOGGER.info("Get chouette jobs status={} action={}", status, action);
        return chouetteJobs.jobsFor(providerId, status, action);
    }

    @DeleteMapping("/services/timetable_admin/{providerId}/jobs")
    public ResponseEntity<String> cancelAllJobs(@PathVariable Long providerId) {
        adminRequest(providerId);
        LOGGER.info("Cancel all chouette jobs");
        chouetteJobs.cancelAllFor(providerId);
        return accepted();
    }

    @DeleteMapping("/services/timetable_admin/{providerId}/jobs/{jobId}")
    public ResponseEntity<String> cancelJob(@PathVariable Long providerId, @PathVariable String jobId) {
        adminRequest(providerId);
        LOGGER.info("Cancel chouette job {}", jobId);
        chouetteJobs.cancel(providerId, jobId);
        return accepted();
    }

    @PostMapping("/services/timetable_admin/{providerId}/export")
    public ResponseEntity<String> export(@PathVariable Long providerId) {
        MardukMessage message = adminRequest(providerId);
        LOGGER.info("Chouette start export");
        publisher.publish(MardukQueues.CHOUETTE_EXPORT_NETEX_QUEUE, message);
        return accepted();
    }

    @PostMapping("/services/timetable_admin/{providerId}/validate")
    public ResponseEntity<String> validate(@PathVariable Long providerId) {
        MardukMessage message = editorRequest(providerId);
        LOGGER.info("Chouette start validation");
        // A provider that migrates its data onwards is only level 1 here; the level 2 run happens in the
        // dataspace it migrates into.
        boolean migrates = providerRepository.getProvider(providerId)
                .getChouetteInfo().getMigrateDataToProvider() != null;
        JobEvent.TimetableAction level = migrates
                ? JobEvent.TimetableAction.VALIDATION_LEVEL_1
                : JobEvent.TimetableAction.VALIDATION_LEVEL_2;
        publisher.publish(MardukQueues.CHOUETTE_VALIDATION_QUEUE,
                message.setHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL, level.name()));
        return accepted();
    }

    @PostMapping("/services/timetable_admin/{providerId}/clean")
    public ResponseEntity<String> clean(@PathVariable Long providerId) {
        adminRequest(providerId);
        chouetteJobs.clean(providerId);
        return accepted();
    }

    @PostMapping("/services/timetable_admin/{providerId}/transfer")
    public ResponseEntity<String> transfer(@PathVariable Long providerId) {
        MardukMessage message = adminRequest(providerId);
        LOGGER.info("Chouette transfer dataspace");
        publisher.publish(MardukQueues.CHOUETTE_TRANSFER_EXPORT_QUEUE, message);
        return accepted();
    }

    // ------------------------------------------------------------------------------------------ osm

    @PostMapping("/services/map_admin/download")
    public ResponseEntity<String> fetchOsmMap() {
        adminRequest();
        LOGGER.info("OSM update map data");
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(osmMapFetcher.fetchIfChanged());
    }

    // -------------------------------------------------------------------------------------- internals

    private ResponseEntity<String> startImport(Long providerId, BlobStoreFiles files, String importType) {
        String username = adminRequest(providerId).getHeader(USERNAME, String.class);
        String referential = providerRepository.getReferential(providerId);
        // Imported in the order they were listed: a later file is meant to win over an earlier one.
        for (BlobStoreFiles.File file : files.getFiles()) {
            MardukMessage message = new MardukMessage()
                    .setHeader(PROVIDER_ID, providerId)
                    .setHeader(CHOUETTE_REFERENTIAL, referential)
                    .setHeader(FILE_HANDLE, Constants.BLOBSTORE_PATH_INBOUND + referential + "/" + file.getName())
                    .setHeader(FILE_NAME, "reimport-" + file.getName())
                    .setHeader(CORRELATION_ID, UUID.randomUUID().toString())
                    .setHeader(USERNAME, username)
                    .setHeaderIfPresent(Constants.IMPORT_TYPE, importType);
            MardukMdc.with(message, () -> {
                LOGGER.info("Chouette start import fileHandle={}", file.getName());
                publisher.publish(MardukQueues.PROCESS_FILE_QUEUE, message);
            });
        }
        return accepted();
    }

    private ResponseEntity<String> upload(
            Long providerId, MultiValueMap<String, MultipartFile> parts, String importType) {
        MardukMessage request = editorRequest(providerId);
        String correlationId = request.getHeader(CORRELATION_ID, String.class);
        String referential = providerRepository.getReferential(providerId);
        MardukMdc.setCodespaceIfMissing(referential);
        LOGGER.info("Upload files and start import pipeline");
        TimetableFileUploader.Upload upload = new TimetableFileUploader.Upload(
                referential, providerId, correlationId, request.getHeader(USERNAME, String.class),
                importType, duplicateFilterWeb, true);
        fileUploader.uploadAll(uploadedFiles(parts), upload);
        return accepted();
    }

    /**
     * Every uploaded part, whatever it is named.
     *
     * <p>Bel names the part after the file rather than {@code file}, and the multipart route it replaced
     * iterated the parts without looking at their names, so neither may be required here.
     */
    private static List<MultipartFile> uploadedFiles(MultiValueMap<String, MultipartFile> parts) {
        return parts.values().stream().flatMap(List::stream).toList();
    }

    private ResponseEntity<Resource> blobResponse(String fileHandle) {
        InputStream blob = internalBlobStore.getBlob(fileHandle);
        if (blob == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(X_OCTET_STREAM))
                .body(new InputStreamResource(blob));
    }

    /**
     * The routes answered a command with an empty body; the status code carries the outcome.
     *
     * <p>The content type is set here rather than declared on the mapping: {@code produces} makes Spring
     * answer 406 when the request's {@code Accept} does not list it, and Ninkasi sends
     * {@code Accept: application/json} to every endpoint including these. Setting it on the response also
     * stops Spring negotiating it into something else.
     */
    private static ResponseEntity<String> accepted() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("");
    }

    /**
     * A message for an administrator action, with the caller recorded on it.
     *
     * <p>{@code USERNAME} is what nabu shows as the person who triggered a job; the routes set it in
     * {@code direct:setUsername}, reached from every authorization route, so every action had it.
     */
    private MardukMessage adminRequest() {
        authorizationService.verifyAdministratorPrivileges();
        return newRequest();
    }

    private MardukMessage adminRequest(Long providerId) {
        authorizationService.verifyAdministratorPrivileges();
        validateProvider(providerId);
        return newRequest().setHeader(PROVIDER_ID, providerId);
    }

    private MardukMessage editorRequest(Long providerId) {
        authorizationService.verifyRouteDataEditorPrivileges(providerId);
        validateProvider(providerId);
        return newRequest().setHeader(PROVIDER_ID, providerId);
    }

    private MardukMessage newRequest() {
        return new MardukMessage()
                .setHeader(CORRELATION_ID, newCorrelationId())
                .setHeader(USERNAME, usernameService.getPreferredUsername());
    }

    private String newCorrelationId() {
        String correlationId = UUID.randomUUID().toString();
        MardukMdc.setCorrelationId(correlationId);
        return correlationId;
    }

    private void validateProvider(Long providerId) {
        if (providerRepository.getProvider(providerId) == null) {
            throw new NotFoundException("Unknown provider id");
        }
    }

    private Long providerIdOf(String codespace) {
        Long providerId = providerRepository.getProviderId(codespace);
        if (providerId == null) {
            throw new NotFoundException("Unknown chouette referential");
        }
        return providerId;
    }

    private void requireHttpImportEnabled() {
        if (!httpImportEnabled) {
            throw new HttpImportDisabledException();
        }
    }

    /** {@code netex.import.http.autoStartup=false} used to leave the route unstarted, which failed the send. */
    static class HttpImportDisabledException extends RuntimeException {
    }
}
