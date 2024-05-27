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

import com.google.cloud.storage.Blob;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.domain.Provider;
import org.apache.commons.lang3.StringUtils;
import org.rutebanken.helper.gcp.BlobStoreHelper;
import org.rutebanken.helper.gcp.repository.GcsBlobStoreRepository;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 * Blob store repository targeting Google Cloud Storage.
 */
public class GcsMardukBlobStoreRepository extends GcsBlobStoreRepository implements MardukBlobStoreRepository {

    private String containerName;

    private final ProviderRepository providerRepository;

    public GcsMardukBlobStoreRepository(String projectId, String credentialPath, ProviderRepository providerRepository) {
        super(projectId, credentialPath);
        this.providerRepository = providerRepository;
    }

    @Override
    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    @Override
    public BlobStoreFiles listBlobs(Collection<String> prefixes) {
        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();


        for (String prefix : prefixes) {
            Iterator<Blob> blobIterator = BlobStoreHelper.listAllBlobsRecursively(storage(), containerName, prefix);
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
        Iterator<Blob> blobIterator = BlobStoreHelper.listAllBlobsRecursively(storage(), containerName, prefix);
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

        file.setUrl(blob.getMediaLink());

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
