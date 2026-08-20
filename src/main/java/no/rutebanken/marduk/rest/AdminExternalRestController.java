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
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.rest.openapi.api.DatasetsApi;
import no.rutebanken.marduk.rest.openapi.api.FlexDatasetsApi;
import no.rutebanken.marduk.rest.openapi.model.UploadResult;
import no.rutebanken.marduk.security.MardukAuthorizationService;
import no.rutebanken.marduk.security.UsernameService;
import no.rutebanken.marduk.upload.TimetableFileUploader;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.*;

/**
 * Spring REST API endpoints for managing the transit data import pipeline.
 * These endpoints are intended to be used by machine-to-machine clients.
 */
@RestController
@RequestMapping("/services/timetable-management")
public class AdminExternalRestController implements DatasetsApi, FlexDatasetsApi {

    private static final Logger LOG = LoggerFactory.getLogger(AdminExternalRestController.class);

    private final MardukAuthorizationService mardukAuthorizationService;
    private final ProviderRepository providerRepository;
    private final MardukInternalBlobStoreService blobStoreService;
    private final TimetableFileUploader fileUploader;
    private final UsernameService usernameService;
    private final boolean duplicateFilterRest;

    public AdminExternalRestController(
            MardukAuthorizationService mardukAuthorizationService,
            ProviderRepository providerRepository,
            MardukInternalBlobStoreService blobStoreService,
            TimetableFileUploader fileUploader,
            UsernameService usernameService,
            @Value("${duplicate.filter.rest:true}") boolean duplicateFilterRest) {
        this.mardukAuthorizationService = mardukAuthorizationService;
        this.providerRepository = providerRepository;
        this.blobStoreService = blobStoreService;
        this.fileUploader = fileUploader;
        this.usernameService = usernameService;
        this.duplicateFilterRest = duplicateFilterRest;
    }

    @Override
    public ResponseEntity<UploadResult> upload(String codespace, MultipartFile file) {
        String correlationId = UUID.randomUUID().toString();
        LOG.info("[{}] Received file from provider {} through the Spring HTTP endpoint", correlationId, codespace);

        Long providerId = validateAndGetProviderId(codespace);
        mardukAuthorizationService.verifyRouteDataEditorPrivileges(providerId);

        LOG.info("[{}] Authorization OK for Spring HTTP endpoint, uploading files and starting import pipeline", correlationId);

        upload(file, codespace, providerId, correlationId, null);

        return ResponseEntity.ok(new UploadResult().correlationId(correlationId));
    }

    @Override
    public ResponseEntity<UploadResult> uploadFlexDataset(String codespace, MultipartFile file) {
        String correlationId = UUID.randomUUID().toString();
        LOG.info("[{}] Received flex file from provider {} through the Spring HTTP endpoint", correlationId, codespace);

        Long providerId = validateAndGetProviderId(codespace);
        mardukAuthorizationService.verifyRouteDataEditorPrivileges(providerId);

        LOG.info("[{}] Authorization OK for Spring HTTP endpoint, uploading flex files and starting import pipeline", correlationId);

        upload(file, codespace, providerId, correlationId, IMPORT_TYPE_NETEX_FLEX);

        return ResponseEntity.ok(new UploadResult().correlationId(correlationId));
    }

    @Override
    public ResponseEntity<Resource> download(String codespace) {
        String correlationId = UUID.randomUUID().toString();
        LOG.info("[{}] Received Blocks download request for provider {} through the Spring HTTP endpoint", correlationId, codespace);

        Long providerId = validateAndGetProviderId(codespace);
        mardukAuthorizationService.verifyBlockViewerPrivileges(providerId);

        String fileHandle = Constants.BLOBSTORE_PATH_NETEX_BLOCKS_EXPORT
                + "rb_" + codespace.toLowerCase()
                + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;

        LOG.info("[{}] Downloading NeTEx dataset with blocks: {}", correlationId, fileHandle);

        InputStream blob = blobStoreService.getBlob(fileHandle);
        if (blob == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] content = blob.readAllBytes();
            return ResponseEntity.ok(new ByteArrayResource(content));
        } catch (IOException e) {
            LOG.error("[{}] Failed to read blob: {}", correlationId, fileHandle, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private void upload(MultipartFile file, String codespace, Long providerId, String correlationId, String importType) {
        fileUploader.upload(file, new TimetableFileUploader.Upload(
                codespace, providerId, correlationId, usernameService.getPreferredUsername(),
                importType, duplicateFilterRest, false));
    }

    private Long validateAndGetProviderId(String codespace) {
        Long providerId = providerRepository.getProviderId(codespace);
        if (providerId == null) {
            throw new NotFoundException("Unknown chouette referential: " + codespace);
        }
        return providerId;
    }

}
