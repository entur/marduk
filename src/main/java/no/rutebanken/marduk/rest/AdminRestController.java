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

package no.rutebanken.marduk.rest;

import jakarta.ws.rs.NotFoundException;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.domain.OtpGraphsInfo;
import no.rutebanken.marduk.rest.openapi.model.UploadResult;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.security.MardukAuthorizationService;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.rutebanken.helper.organisation.NotAuthenticatedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.*;

/**
 * Spring REST API endpoints mirroring AdminRestRouteBuilder.
 * Paths are postfixed with "_new" to distinguish from the Camel-based routes.
 */
@RestController
@RequestMapping("/services")
public class AdminRestController {

    private static final Logger LOG = LoggerFactory.getLogger(AdminRestController.class);

    private final MardukAuthorizationService mardukAuthorizationService;
    private final ProviderRepository providerRepository;
    private final ProducerTemplate producerTemplate;
    private final CamelContext camelContext;

    public AdminRestController(
            MardukAuthorizationService mardukAuthorizationService,
            ProviderRepository providerRepository,
            ProducerTemplate producerTemplate,
            CamelContext camelContext) {
        this.mardukAuthorizationService = mardukAuthorizationService;
        this.providerRepository = providerRepository;
        this.producerTemplate = producerTemplate;
        this.camelContext = camelContext;
    }

    /**
     * Clean unique filename and digest Idempotent Stores.
     */
    @PostMapping("/timetable_admin_new/idempotentfilter/clean")
    public ResponseEntity<Void> cleanIdempotentFilter() {
        LOG.info("Cleaning idempotent file store via Spring endpoint");
        mardukAuthorizationService.verifyAdministratorPrivileges();

        Exchange exchange = ExchangeBuilder.create(camelContext).build();
        producerTemplate.send("direct:cleanIdempotentFileStore", exchange);

        return ResponseEntity.ok().build();
    }

    /**
     * Triggers building of the OTP graph using existing NeTEx and a pre-prepared base graph with map data.
     */
    @PostMapping("/timetable_admin_new/routing_graph/build")
    public ResponseEntity<Void> buildGraph() {
        LOG.info("Triggering OTP graph build via Spring endpoint");
        mardukAuthorizationService.verifyAdministratorPrivileges();

        Exchange exchange = ExchangeBuilder.create(camelContext).build();
        producerTemplate.send("google-pubsub:{{marduk.pubsub.project.id}}:Otp2GraphBuildQueue", exchange);

        return ResponseEntity.ok().build();
    }

    /**
     * Triggers the clean ALL dataspace process in Chouette.
     */
    @PostMapping("/timetable_admin_new/clean/{filter}")
    public ResponseEntity<Void> cleanDataspaces(@PathVariable String filter) {
        LOG.info("Cleaning dataspaces with filter {} via Spring endpoint", filter);
        mardukAuthorizationService.verifyAdministratorPrivileges();

        if (!List.of("all", "level1", "level2").contains(filter)) {
            return ResponseEntity.badRequest().build();
        }

        Exchange exchange = ExchangeBuilder.create(camelContext)
                .withHeader("filter", filter)
                .build();
        producerTemplate.send("direct:chouetteCleanAllReferentials", exchange);

        return ResponseEntity.ok().build();
    }

    /**
     * List Chouette jobs for all providers.
     */
    @GetMapping("/timetable_admin_new/jobs")
    public ResponseEntity<ProviderAndJobs[]> listJobs(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String action) {
        LOG.info("Listing Chouette jobs via Spring endpoint");
        mardukAuthorizationService.verifyAdministratorPrivileges();

        ExchangeBuilder builder = ExchangeBuilder.create(camelContext)
                .withHeader("status", status != null ? status : Arrays.asList("STARTED", "SCHEDULED"));
        if (action != null) {
            builder.withHeader("action", action);
        }

        Exchange result = producerTemplate.send("direct:chouetteGetJobsAll", builder.build());
        ProviderAndJobs[] jobs = result.getMessage().getBody(ProviderAndJobs[].class);

        return ResponseEntity.ok(jobs);
    }

    /**
     * List files containing exported timetable data and graphs.
     */
    @GetMapping("/timetable_admin_new/export/files")
    public ResponseEntity<BlobStoreFiles> listExportFiles() {
        LOG.info("Listing export files via Spring endpoint");
        mardukAuthorizationService.verifyAdministratorPrivileges();

        Exchange exchange = ExchangeBuilder.create(camelContext).build();
        Exchange result = producerTemplate.send("direct:listTimetableExportAndGraphBlobs", exchange);
        BlobStoreFiles files = result.getMessage().getBody(BlobStoreFiles.class);

        return ResponseEntity.ok(files);
    }

    /**
     * Remove completed Chouette jobs for all providers.
     */
    @DeleteMapping("/timetable_admin_new/completed_jobs")
    public ResponseEntity<Void> removeCompletedJobs(
            @RequestParam(required = false) Integer keepJobs,
            @RequestParam(required = false) Integer keepDays) {
        LOG.info("Removing completed jobs via Spring endpoint");
        mardukAuthorizationService.verifyAdministratorPrivileges();

        ExchangeBuilder builder = ExchangeBuilder.create(camelContext);
        if (keepJobs != null) {
            builder.withHeader("keepJobs", keepJobs);
        }
        if (keepDays != null) {
            builder.withHeader("keepDays", keepDays);
        }
        producerTemplate.send("direct:chouetteRemoveOldJobs", builder.build());

        return ResponseEntity.ok().build();
    }

    /**
     * List files available for reimport for a specific provider.
     */
    @GetMapping("/timetable_admin_new/{providerId}/files")
    public ResponseEntity<BlobStoreFiles> listProviderFiles(@PathVariable Long providerId) {
        LOG.info("Listing files for provider {} via Spring endpoint", providerId);
        mardukAuthorizationService.verifyAdministratorPrivileges();
        validateProvider(providerId);

        Exchange exchange = ExchangeBuilder.create(camelContext)
                .withHeader(PROVIDER_ID, providerId)
                .build();
        Exchange result = producerTemplate.send("direct:listInternalBlobsFlat", exchange);
        BlobStoreFiles files = result.getMessage().getBody(BlobStoreFiles.class);

        return ResponseEntity.ok(files);
    }

    /**
     * Triggers import process for files in blob store.
     */
    @PostMapping("/timetable_admin_new/{providerId}/import")
    public ResponseEntity<Void> importFiles(
            @PathVariable Long providerId,
            @RequestBody BlobStoreFiles files) {
        LOG.info("Importing files for provider {} via Spring endpoint", providerId);
        mardukAuthorizationService.verifyAdministratorPrivileges();
        validateProvider(providerId);

        String referential = providerRepository.getReferential(providerId);

        for (BlobStoreFiles.File file : files.getFiles()) {
            String correlationId = UUID.randomUUID().toString();
            Exchange exchange = ExchangeBuilder.create(camelContext)
                    .withHeader(PROVIDER_ID, providerId)
                    .withHeader(CHOUETTE_REFERENTIAL, referential)
                    .withHeader(CORRELATION_ID, correlationId)
                    .withHeader(FILE_HANDLE, Constants.BLOBSTORE_PATH_INBOUND + referential + "/" + file.getName())
                    .withHeader(Constants.FILE_NAME, "reimport-" + file.getName())
                    .withPattern(ExchangePattern.InOnly)
                    .build();

            producerTemplate.send("google-pubsub:{{marduk.pubsub.project.id}}:ProcessFileQueue", exchange);
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Cancel a specific Chouette job for a provider.
     */
    @DeleteMapping("/timetable_admin_new/{providerId}/jobs/{jobId}")
    public ResponseEntity<Void> cancelJob(
            @PathVariable Long providerId,
            @PathVariable Long jobId) {
        LOG.info("Cancelling job {} for provider {} via Spring endpoint", jobId, providerId);
        mardukAuthorizationService.verifyAdministratorPrivileges();
        validateProvider(providerId);

        Exchange exchange = ExchangeBuilder.create(camelContext)
                .withHeader(PROVIDER_ID, providerId)
                .withHeader(Constants.CHOUETTE_JOB_ID, jobId)
                .build();
        producerTemplate.send("direct:chouetteCancelJob", exchange);

        return ResponseEntity.ok().build();
    }

    /**
     * Upload file for import into Chouette.
     */
    @PostMapping("/timetable_admin_new/{providerId}/files")
    public ResponseEntity<Void> uploadFile(
            @PathVariable Long providerId,
            @RequestParam("file") MultipartFile file) throws IOException {
        String correlationId = UUID.randomUUID().toString();
        LOG.info("[{}] Uploading file for provider {} via Spring endpoint", correlationId, providerId);
        mardukAuthorizationService.verifyRouteDataEditorPrivileges(providerId);
        validateProvider(providerId);

        String referential = providerRepository.getReferential(providerId);
        String fileName = file.getOriginalFilename();

        Exchange exchange = ExchangeBuilder.create(camelContext)
                .withHeader(CHOUETTE_REFERENTIAL, referential)
                .withHeader(PROVIDER_ID, providerId)
                .withHeader(CORRELATION_ID, correlationId)
                .withHeader(FILE_APPLY_DUPLICATES_FILTER, true)
                .withHeader(FILE_APPLY_DUPLICATES_FILTER_ON_NAME_ONLY, true)
                .withHeader(FILE_NAME, fileName)
                .withBody(file.getInputStream())
                .build();

        producerTemplate.send("direct:uploadFilesAndStartImport", exchange);

        return ResponseEntity.ok().build();
    }

    // Second batch of endpoints

    /**
     * Cancel all Chouette jobs for all providers.
     */
    @DeleteMapping("/timetable_admin_new/jobs")
    public ResponseEntity<Void> cancelAllJobs() {
        LOG.info("Cancelling all Chouette jobs via Spring endpoint");
        mardukAuthorizationService.verifyAdministratorPrivileges();

        Exchange exchange = ExchangeBuilder.create(camelContext).build();
        producerTemplate.send("direct:chouetteCancelAllJobsForAllProviders", exchange);

        return ResponseEntity.ok().build();
    }

    /**
     * Trigger prevalidation for all providers.
     */
    @PostMapping("/timetable_admin_new/validate/prevalidation")
    public ResponseEntity<Void> triggerPrevalidation() {
        LOG.info("Triggering prevalidation for all providers via Spring endpoint");
        mardukAuthorizationService.verifyAdministratorPrivileges();

        Exchange exchange = ExchangeBuilder.create(camelContext)
                .withPattern(ExchangePattern.InOnly)
                .build();
        producerTemplate.send("direct:triggerAntuValidationForAllProviders", exchange);

        return ResponseEntity.ok().build();
    }

    /**
     * Trigger level2 validation for all providers.
     */
    @PostMapping("/timetable_admin_new/validate/level2")
    public ResponseEntity<Void> triggerLevel2Validation() {
        LOG.info("Triggering level2 validation for all providers via Spring endpoint");
        mardukAuthorizationService.verifyAdministratorPrivileges();

        Exchange exchange = ExchangeBuilder.create(camelContext)
                .withPattern(ExchangePattern.InOnly)
                .build();
        producerTemplate.send("direct:chouetteValidateLevel2ForAllProviders", exchange);

        return ResponseEntity.ok().build();
    }

    /**
     * Clean all stop places in Chouette.
     */
    @PostMapping("/timetable_admin_new/stop_places/clean")
    public ResponseEntity<Void> cleanStopPlaces() {
        LOG.info("Cleaning stop places via Spring endpoint");
        mardukAuthorizationService.verifyAdministratorPrivileges();

        Exchange exchange = ExchangeBuilder.create(camelContext).build();
        producerTemplate.send("direct:chouetteCleanStopPlaces", exchange);

        return ResponseEntity.ok().build();
    }

    /**
     * Refresh line statistics cache.
     */
    @PostMapping("/timetable_admin_new/line_statistics/refresh")
    public ResponseEntity<Void> refreshLineStatistics() {
        LOG.info("Refreshing line statistics cache via Spring endpoint");
        mardukAuthorizationService.verifyAdministratorPrivileges();

        Exchange exchange = ExchangeBuilder.create(camelContext).build();
        producerTemplate.send("direct:chouetteRefreshStatsCache", exchange);

        return ResponseEntity.ok().build();
    }

    /**
     * Trigger OTP base graph build.
     */
    @PostMapping("/timetable_admin_new/routing_graph/build_base")
    public ResponseEntity<Void> buildBaseGraph() {
        LOG.info("Triggering OTP base graph build via Spring endpoint");
        mardukAuthorizationService.verifyAdministratorPrivileges();

        Exchange exchange = ExchangeBuilder.create(camelContext)
                .withPattern(ExchangePattern.InOnly)
                .build();
        producerTemplate.send("google-pubsub:{{marduk.pubsub.project.id}}:Otp2BaseGraphBuildQueue", exchange);

        return ResponseEntity.ok().build();
    }

    /**
     * Trigger candidate graph build.
     */
    @PostMapping("/timetable_admin_new/routing_graph/build_candidate/{graphType}")
    public ResponseEntity<String> buildCandidateGraph(@PathVariable String graphType) {
        LOG.info("Triggering OTP candidate graph build for type {} via Spring endpoint", graphType);
        mardukAuthorizationService.verifyAdministratorPrivileges();

        String destination;
        if ("otp2_base".equals(graphType)) {
            destination = "google-pubsub:{{marduk.pubsub.project.id}}:Otp2BaseGraphCandidateBuildQueue";
        } else if ("otp2_netex".equals(graphType)) {
            destination = "google-pubsub:{{marduk.pubsub.project.id}}:Otp2GraphCandidateBuildQueue";
        } else {
            return ResponseEntity.badRequest().body("Unknown Graph Type");
        }

        Exchange exchange = ExchangeBuilder.create(camelContext)
                .withPattern(ExchangePattern.InOnly)
                .build();
        producerTemplate.send(destination, exchange);

        return ResponseEntity.ok().build();
    }

    /**
     * List OTP2 graphs.
     */
    @GetMapping("/timetable_admin_new/routing_graph/graphs")
    public ResponseEntity<OtpGraphsInfo[]> listGraphs() {
        LOG.info("Listing OTP graphs via Spring endpoint");
        mardukAuthorizationService.verifyAdministratorPrivileges();

        Exchange exchange = ExchangeBuilder.create(camelContext).build();
        Exchange result = producerTemplate.send("direct:listGraphs", exchange);
        OtpGraphsInfo[] graphs = result.getMessage().getBody(OtpGraphsInfo[].class);

        return ResponseEntity.ok(graphs);
    }

    /**
     * Cancel all Chouette jobs for a specific provider.
     */
    @DeleteMapping("/timetable_admin_new/{providerId}/jobs")
    public ResponseEntity<Void> cancelAllProviderJobs(@PathVariable Long providerId) {
        LOG.info("Cancelling all jobs for provider {} via Spring endpoint", providerId);
        mardukAuthorizationService.verifyAdministratorPrivileges();
        validateProvider(providerId);

        Exchange exchange = ExchangeBuilder.create(camelContext)
                .withHeader(PROVIDER_ID, providerId)
                .build();
        producerTemplate.send("direct:chouetteCancelAllJobsForProvider", exchange);

        return ResponseEntity.ok().build();
    }

    /**
     * Triggers flex data import process for files in blob store.
     */
    @PostMapping("/timetable_admin_new/{providerId}/flex/import")
    public ResponseEntity<Void> importFlexFiles(
            @PathVariable Long providerId,
            @RequestBody BlobStoreFiles files) {
        LOG.info("Importing flex files for provider {} via Spring endpoint", providerId);
        mardukAuthorizationService.verifyAdministratorPrivileges();
        validateProvider(providerId);

        String referential = providerRepository.getReferential(providerId);

        for (BlobStoreFiles.File file : files.getFiles()) {
            String correlationId = UUID.randomUUID().toString();
            Exchange exchange = ExchangeBuilder.create(camelContext)
                    .withHeader(PROVIDER_ID, providerId)
                    .withHeader(CHOUETTE_REFERENTIAL, referential)
                    .withHeader(CORRELATION_ID, correlationId)
                    .withHeader(FILE_HANDLE, Constants.BLOBSTORE_PATH_INBOUND + referential + "/" + file.getName())
                    .withHeader(Constants.FILE_NAME, "reimport-" + file.getName())
                    .withHeader(IMPORT_TYPE, IMPORT_TYPE_NETEX_FLEX)
                    .withPattern(ExchangePattern.InOnly)
                    .build();

            producerTemplate.send("google-pubsub:{{marduk.pubsub.project.id}}:ProcessFileQueue", exchange);
        }

        return ResponseEntity.ok().build();
    }

    // Third batch of endpoints

    /**
     * Get line statistics for multiple providers.
     */
    @GetMapping("/timetable_admin_new/line_statistics/{filter}")
    public ResponseEntity<String> getLineStatistics(
            @PathVariable String filter,
            @RequestParam(required = false) String providerIds) {
        LOG.info("Getting line statistics with filter {} via Spring endpoint", filter);
        mardukAuthorizationService.verifyAdministratorPrivileges();

        if (!List.of("all", "level1", "level2").contains(filter)) {
            return ResponseEntity.badRequest().body("Invalid filter");
        }

        ExchangeBuilder builder = ExchangeBuilder.create(camelContext)
                .withHeader("filter", filter);
        if (providerIds != null) {
            builder.withHeader(PROVIDER_IDS, providerIds.split(","));
        }

        Exchange result = producerTemplate.send("direct:chouetteGetStats", builder.build());
        String stats = result.getMessage().getBody(String.class);

        return ResponseEntity.ok(stats);
    }

    /**
     * Trigger merged GTFS export.
     */
    @PostMapping("/timetable_admin_new/export/gtfs/merged")
    public ResponseEntity<Void> triggerMergedGtfsExport() {
        LOG.info("Triggering merged GTFS export via Spring endpoint");
        mardukAuthorizationService.verifyAdministratorPrivileges();

        Exchange exchange = ExchangeBuilder.create(camelContext)
                .withPattern(ExchangePattern.InOnly)
                .build();
        producerTemplate.send("google-pubsub:{{marduk.pubsub.project.id}}:GtfsExportMergedQueue", exchange);

        return ResponseEntity.ok().build();
    }

    /**
     * Download file for reimport for a specific provider.
     */
    @GetMapping("/timetable_admin_new/{providerId}/files/{fileName}")
    public ResponseEntity<byte[]> downloadProviderFile(
            @PathVariable Long providerId,
            @PathVariable String fileName) {
        LOG.info("Downloading file {} for provider {} via Spring endpoint", fileName, providerId);
        mardukAuthorizationService.verifyAdministratorPrivileges();
        validateProvider(providerId);

        String decodedFileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);
        String referential = providerRepository.getReferential(providerId);
        String fileHandle = Constants.BLOBSTORE_PATH_INBOUND + referential + "/" + decodedFileName;

        Exchange exchange = ExchangeBuilder.create(camelContext)
                .withHeader(FILE_HANDLE, fileHandle)
                .build();
        Exchange result = producerTemplate.send("direct:getInternalBlob", exchange);

        byte[] content = result.getMessage().getBody(byte[].class);
        if (content == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(content);
    }

    /**
     * Get line statistics for a specific provider.
     */
    @GetMapping("/timetable_admin_new/{providerId}/line_statistics")
    public ResponseEntity<String> getProviderLineStatistics(@PathVariable Long providerId) {
        LOG.info("Getting line statistics for provider {} via Spring endpoint", providerId);
        mardukAuthorizationService.verifyRouteDataEditorPrivileges(providerId);
        validateProvider(providerId);

        Exchange exchange = ExchangeBuilder.create(camelContext)
                .withHeader(PROVIDER_ID, providerId)
                .build();
        Exchange result = producerTemplate.send("direct:chouetteGetStatsSingleProvider", exchange);
        String stats = result.getMessage().getBody(String.class);

        return ResponseEntity.ok(stats);
    }

    /**
     * List jobs for a specific provider.
     */
    @GetMapping("/timetable_admin_new/{providerId}/jobs")
    public ResponseEntity<JobResponse[]> listProviderJobs(
            @PathVariable Long providerId,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String action) {
        LOG.info("Listing jobs for provider {} via Spring endpoint", providerId);
        mardukAuthorizationService.verifyAdministratorPrivileges();
        validateProvider(providerId);

        ExchangeBuilder builder = ExchangeBuilder.create(camelContext)
                .withHeader(PROVIDER_ID, providerId);
        if (status != null) {
            builder.withHeader("status", status);
        }
        if (action != null) {
            builder.withHeader("action", action);
        }

        Exchange result = producerTemplate.send("direct:chouetteGetJobsForProvider", builder.build());
        JobResponse[] jobs = result.getMessage().getBody(JobResponse[].class);

        return ResponseEntity.ok(jobs);
    }

    /**
     * Trigger export for a specific provider.
     */
    @PostMapping("/timetable_admin_new/{providerId}/export")
    public ResponseEntity<Void> triggerProviderExport(@PathVariable Long providerId) {
        LOG.info("Triggering export for provider {} via Spring endpoint", providerId);
        mardukAuthorizationService.verifyAdministratorPrivileges();
        validateProvider(providerId);

        Exchange exchange = ExchangeBuilder.create(camelContext)
                .withHeader(PROVIDER_ID, providerId)
                .withPattern(ExchangePattern.InOnly)
                .build();
        producerTemplate.send("google-pubsub:{{marduk.pubsub.project.id}}:ChouetteExportNetexQueue", exchange);

        return ResponseEntity.ok().build();
    }

    /**
     * Trigger validation for a specific provider.
     */
    @PostMapping("/timetable_admin_new/{providerId}/validate")
    public ResponseEntity<Void> triggerProviderValidation(@PathVariable Long providerId) {
        LOG.info("Triggering validation for provider {} via Spring endpoint", providerId);
        mardukAuthorizationService.verifyRouteDataEditorPrivileges(providerId);
        validateProvider(providerId);

        // Determine validation level based on provider configuration
        String validationLevel;
        if (providerRepository.getProvider(providerId).getChouetteInfo().getMigrateDataToProvider() == null) {
            validationLevel = JobEvent.TimetableAction.VALIDATION_LEVEL_2.name();
        } else {
            validationLevel = JobEvent.TimetableAction.VALIDATION_LEVEL_1.name();
        }

        Exchange exchange = ExchangeBuilder.create(camelContext)
                .withHeader(PROVIDER_ID, providerId)
                .withHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL, validationLevel)
                .withPattern(ExchangePattern.InOnly)
                .build();
        producerTemplate.send("google-pubsub:{{marduk.pubsub.project.id}}:ChouetteValidationQueue", exchange);

        return ResponseEntity.ok().build();
    }

    /**
     * Clean dataspace for a specific provider.
     */
    @PostMapping("/timetable_admin_new/{providerId}/clean")
    public ResponseEntity<Void> cleanProviderDataspace(@PathVariable Long providerId) {
        LOG.info("Cleaning dataspace for provider {} via Spring endpoint", providerId);
        mardukAuthorizationService.verifyAdministratorPrivileges();
        validateProvider(providerId);

        Exchange exchange = ExchangeBuilder.create(camelContext)
                .withHeader(PROVIDER_ID, providerId)
                .build();
        producerTemplate.send("direct:chouetteCleanReferential", exchange);

        return ResponseEntity.ok().build();
    }

    /**
     * Transfer data for a specific provider.
     */
    @PostMapping("/timetable_admin_new/{providerId}/transfer")
    public ResponseEntity<Void> transferProviderData(@PathVariable Long providerId) {
        LOG.info("Triggering data transfer for provider {} via Spring endpoint", providerId);
        mardukAuthorizationService.verifyAdministratorPrivileges();
        validateProvider(providerId);

        Exchange exchange = ExchangeBuilder.create(camelContext)
                .withHeader(PROVIDER_ID, providerId)
                .withPattern(ExchangePattern.InOnly)
                .build();
        producerTemplate.send("google-pubsub:{{marduk.pubsub.project.id}}:ChouetteTransferExportQueue", exchange);

        return ResponseEntity.ok().build();
    }

    /**
     * Upload flexible line file for import.
     */
    @PostMapping("/timetable_admin_new/{providerId}/flex/files")
    public ResponseEntity<Void> uploadFlexFile(
            @PathVariable Long providerId,
            @RequestParam("file") MultipartFile file) throws IOException {
        String correlationId = UUID.randomUUID().toString();
        LOG.info("[{}] Uploading flex file for provider {} via Spring endpoint", correlationId, providerId);
        mardukAuthorizationService.verifyRouteDataEditorPrivileges(providerId);
        validateProvider(providerId);

        String referential = providerRepository.getReferential(providerId);
        String fileName = file.getOriginalFilename();

        Exchange exchange = ExchangeBuilder.create(camelContext)
                .withHeader(CHOUETTE_REFERENTIAL, referential)
                .withHeader(PROVIDER_ID, providerId)
                .withHeader(CORRELATION_ID, correlationId)
                .withHeader(FILE_APPLY_DUPLICATES_FILTER, true)
                .withHeader(FILE_APPLY_DUPLICATES_FILTER_ON_NAME_ONLY, true)
                .withHeader(FILE_NAME, fileName)
                .withHeader(IMPORT_TYPE, IMPORT_TYPE_NETEX_FLEX)
                .withBody(file.getInputStream())
                .build();

        producerTemplate.send("direct:uploadFilesAndStartImport", exchange);

        return ResponseEntity.ok().build();
    }

    // Map admin endpoints

    /**
     * Triggers downloading of the latest OSM data.
     */
    @PostMapping("/map_admin_new/download")
    public ResponseEntity<Void> downloadOsmData() {
        LOG.info("Triggering OSM data download via Spring endpoint");
        mardukAuthorizationService.verifyAdministratorPrivileges();

        Exchange exchange = ExchangeBuilder.create(camelContext).build();
        producerTemplate.send("direct:considerToFetchOsmMapOverNorway", exchange);

        return ResponseEntity.ok().build();
    }

    // Deprecated codespace-based endpoints

    /**
     * Upload NeTEx file by codespace.
     * @deprecated Use {@link #uploadFile(Long, MultipartFile)} with providerId instead.
     */
    @Deprecated
    @PostMapping("/timetable_admin_new/upload/{codespace}")
    public ResponseEntity<UploadResult> uploadFileByCodespace(
            @PathVariable String codespace,
            @RequestParam("file") MultipartFile file) throws IOException {
        String correlationId = UUID.randomUUID().toString();
        LOG.info("[{}] Uploading file for codespace {} via Spring endpoint", correlationId, codespace);

        Long providerId = validateCodespace(codespace);
        mardukAuthorizationService.verifyRouteDataEditorPrivileges(providerId);

        Exchange exchange = ExchangeBuilder.create(camelContext)
                .withHeader(CHOUETTE_REFERENTIAL, codespace)
                .withHeader(PROVIDER_ID, providerId)
                .withHeader(CORRELATION_ID, correlationId)
                .withHeader(FILE_APPLY_DUPLICATES_FILTER, true)
                .withHeader(FILE_NAME, file.getOriginalFilename())
                .withBody(file.getInputStream())
                .build();

        producerTemplate.send("direct:uploadFilesAndStartImport", exchange);

        UploadResult result = new UploadResult().correlationId(correlationId);
        return ResponseEntity.ok(result);
    }

    /**
     * Download NeTEx dataset with blocks by codespace.
     * @deprecated Use provider-specific endpoints instead.
     */
    @Deprecated
    @GetMapping("/timetable_admin_new/download_netex_blocks/{codespace}")
    public ResponseEntity<byte[]> downloadNetexBlocks(@PathVariable String codespace) {
        String correlationId = UUID.randomUUID().toString();
        LOG.info("[{}] Downloading NeTEx blocks for codespace {} via Spring endpoint", correlationId, codespace);

        Long providerId = validateCodespace(codespace);
        mardukAuthorizationService.verifyBlockViewerPrivileges(providerId);

        String fileHandle = Constants.BLOBSTORE_PATH_NETEX_BLOCKS_EXPORT
                + "rb_" + codespace.toLowerCase()
                + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;

        Exchange exchange = ExchangeBuilder.create(camelContext)
                .withHeader(FILE_HANDLE, fileHandle)
                .build();
        Exchange result = producerTemplate.send("direct:getInternalBlob", exchange);

        byte[] content = result.getMessage().getBody(byte[].class);
        if (content == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(content);
    }

    private void validateProvider(Long providerId) {
        if (providerRepository.getProvider(providerId) == null) {
            throw new NotFoundException("Unknown provider id: " + providerId);
        }
    }

    private Long validateCodespace(String codespace) {
        Long providerId = providerRepository.getProviderId(codespace);
        if (providerId == null) {
            throw new NotFoundException("Unknown chouette referential: " + codespace);
        }
        return providerId;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }

    @ExceptionHandler(NotAuthenticatedException.class)
    public ResponseEntity<String> handleNotAuthenticated(NotAuthenticatedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<String> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}
