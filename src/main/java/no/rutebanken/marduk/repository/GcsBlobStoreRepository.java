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

package no.rutebanken.marduk.repository;

import com.google.cloud.storage.Acl;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.domain.Provider;
import org.apache.commons.lang3.StringUtils;
import org.rutebanken.helper.gcp.BlobStoreHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Blob store repository targeting Google Cloud Storage.
 */
@Repository
@Profile("gcs-blobstore")
@Scope("prototype")
public class GcsBlobStoreRepository implements BlobStoreRepository {

    @Autowired
    private Storage storage;

    private String containerName;

    @Autowired
    private ProviderRepository providerRepository;

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    @Override
    public BlobStoreFiles listBlobs(Collection<String> prefixes) {
        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();


        for (String prefix : prefixes) {
            Iterator<Blob> blobIterator = BlobStoreHelper.listAllBlobsRecursively(storage, containerName, prefix);
            blobIterator.forEachRemaining(blob -> blobStoreFiles.add(toBlobStoreFile(blob, blob.getName())));
        }

        return blobStoreFiles;
    }

    @Override
    public BlobStoreFiles listBlobs(String prefix) {
        return listBlobs(Collections.singletonList(prefix));
    }


    @Override
    public BlobStoreFiles listBlobsFlat(String prefix) {
        Iterator<Blob> blobIterator = BlobStoreHelper.listAllBlobsRecursively(storage, containerName, prefix);
        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();
        while (blobIterator.hasNext()) {
            Blob blob = blobIterator.next();
            String fileName = blob.getName().replace(prefix, "");
            if (!StringUtils.isEmpty(fileName)) {
                blobStoreFiles.add(toBlobStoreFile(blob, fileName));
            }
        }

        return blobStoreFiles;
    }

    @Override
    public InputStream getBlob(String name) {
        return BlobStoreHelper.getBlob(storage, containerName, name);
    }

    @Override
    public long uploadBlob(String name, InputStream inputStream, boolean makePublic) {
        Blob blob = BlobStoreHelper.createOrReplace(storage, containerName, name, inputStream, makePublic);
        return blob.getGeneration();
    }

    @Override
    public long uploadBlob(String name, InputStream inputStream, boolean makePublic, String contentType) {
        Blob blob = BlobStoreHelper.createOrReplace(storage, containerName, name, inputStream, makePublic, contentType);
        return blob.getGeneration();
    }

    @Override
    public void copyBlob(String sourceContainerName, String sourceObjectName, String targetContainerName, String targetObjectName, boolean makePublic) {
        copyVersionedBlob(sourceContainerName, sourceObjectName, null, targetContainerName, targetObjectName, makePublic);
    }

    @Override
    public void copyVersionedBlob(String sourceContainerName, String sourceObjectName, Long sourceVersion, String targetContainerName, String targetObjectName, boolean makePublic) {

        List<Storage.BlobTargetOption> blobTargetOptions = makePublic ? List.of(Storage.BlobTargetOption.predefinedAcl(Storage.PredefinedAcl.PUBLIC_READ))
                : Collections.emptyList();
        Storage.CopyRequest request =
                Storage.CopyRequest.newBuilder()
                        .setSource(BlobId.of(sourceContainerName, sourceObjectName, sourceVersion))
                        .setTarget(BlobId.of(targetContainerName, targetObjectName), blobTargetOptions)
                        .build();
        storage.copy(request).getResult();
    }

    @Override
    public void copyAllBlobs(String sourceContainerName, String prefix, String targetContainerName, String targetPrefix, boolean makePublic) {
        Iterator<Blob> blobIterator = BlobStoreHelper.listAllBlobsRecursively(storage, sourceContainerName, prefix);
        while (blobIterator.hasNext()) {
            Blob blob = blobIterator.next();

            List<Storage.BlobTargetOption> blobTargetOptions = makePublic ? List.of(Storage.BlobTargetOption.predefinedAcl(Storage.PredefinedAcl.PUBLIC_READ))
                    : Collections.emptyList();

            BlobInfo.Builder targetBlobInfoBuilder = BlobInfo.newBuilder(targetContainerName, blob.getName().replace(prefix, targetPrefix));
            BlobId targetBlobId = targetBlobInfoBuilder.build().getBlobId();

            Storage.CopyRequest request =
                    Storage.CopyRequest.newBuilder()
                            .setSource(blob.getBlobId())
                            .setTarget(targetBlobId, blobTargetOptions)
                            .build();
            Blob targetBlob = storage.copy(request).getResult();

            if (targetBlob.getName().endsWith(".html")) {
                BlobInfo updatedInfo = targetBlob.toBuilder().setContentType("text/html").build();
                storage.update(updatedInfo);
            }
        }
    }

    @Override
    public boolean delete(String objectName) {
        return BlobStoreHelper.delete(storage, BlobId.of(containerName, objectName));
    }

    @Override
    public boolean deleteAllFilesInFolder(String folder) {
        return BlobStoreHelper.deleteBlobsByPrefix(storage, containerName, folder);
    }


    private BlobStoreFiles.File toBlobStoreFile(Blob blob, String fileName) {
        BlobStoreFiles.File file = new BlobStoreFiles.File(fileName, blob.getCreateTimeOffsetDateTime().toInstant(), blob.getUpdateTimeOffsetDateTime().toInstant(), blob.getSize());
        Provider provider = null;
        if (file.getName().contains("graphs/")) {
            file.setFormat(BlobStoreFiles.File.Format.GRAPH);
        } else if (file.getName().contains("/netex/")) {
            file.setFormat(BlobStoreFiles.File.Format.NETEX);
            provider = parseProviderFromFileName(file.getName());
        } else if (file.getName().contains("/gtfs/")) {
            file.setFormat(BlobStoreFiles.File.Format.GTFS);
            provider = parseProviderFromFileName(file.getName());
        } else {
            file.setFormat(BlobStoreFiles.File.Format.UNKOWN);
        }

        if (provider != null) {
            file.setProviderId(provider.getId());
            file.setReferential(provider.getChouetteInfo().getReferential());
        }

        if (blob.getAcl() != null) {
            if (blob.getAcl().stream().anyMatch(acl -> Acl.User.ofAllUsers().equals(acl.getEntity()) && acl.getRole() != null)) {
                file.setUrl(blob.getMediaLink());
            }
        }
        return file;
    }


    private Provider parseProviderFromFileName(String fileName) {
        if (fileName == null) {
            return null;
        }

        String[] fileParts = fileName.split("/");
        String potentialRef = fileParts[fileParts.length - 1].split("-")[0];


        return providerRepository.getProviders().stream().filter(provider -> potentialRef.equalsIgnoreCase((provider.getChouetteInfo().getReferential()))).findFirst().orElse(null);
    }
}
