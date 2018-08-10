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
import com.google.cloud.storage.Storage;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.domain.Provider;
import org.apache.commons.lang3.StringUtils;
import org.rutebanken.helper.gcp.BlobStoreHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

@Repository
@Profile("gcs-blobstore")
@Scope("prototype")
public class GcsBlobStoreRepository implements BlobStoreRepository {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private Storage storage;

    private String containerName;

    @Autowired
    private ProviderRepository providerRepository;

    @Override
    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    @Override
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
        return listBlobs(Arrays.asList(prefix));
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
    public void uploadBlob(String name, InputStream inputStream, boolean makePublic) {
        BlobStoreHelper.uploadBlobWithRetry(storage, containerName, name, inputStream, makePublic);
    }

    @Override
    public void uploadBlob(String name, InputStream inputStream, boolean makePublic, String contentType) {
        BlobStoreHelper.uploadBlobWithRetry(storage, containerName, name, inputStream, makePublic, contentType);
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
        BlobStoreFiles.File file = new BlobStoreFiles.File(fileName, new Date(blob.getCreateTime()), new Date(blob.getUpdateTime()), blob.getSize());
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
            file.setProviderId(provider.id);
            file.setReferential(provider.chouetteInfo.referential);
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


        return providerRepository.getProviders().stream().filter(provider -> potentialRef.equalsIgnoreCase((provider.chouetteInfo.referential))).findFirst().orElse(null);
    }
}
