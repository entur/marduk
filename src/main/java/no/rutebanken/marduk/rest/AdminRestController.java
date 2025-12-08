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
import no.rutebanken.marduk.repository.ProviderRepository;
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

    private void validateProvider(Long providerId) {
        if (providerRepository.getProvider(providerId) == null) {
            throw new NotFoundException("Unknown provider id: " + providerId);
        }
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
