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

import com.google.cloud.storage.Storage;
import no.rutebanken.marduk.domain.BlobStoreFiles;

import java.io.InputStream;
import java.util.Collection;

/**
 * Repository interface for managing binary files.
 * The main implementation {@link GcsBlobStoreRepository} targets Google Cloud Storage.
 * A simple implementation {@link LocalDiskBlobStoreRepository} is available for testing in a local environment.
 */
public interface BlobStoreRepository {

    BlobStoreFiles listBlobs(Collection<String> prefixes);

    BlobStoreFiles listBlobs(String prefix);

    BlobStoreFiles listBlobsFlat(String prefix);

    InputStream getBlob(String objectName);

    void uploadBlob(String objectName, InputStream inputStream, boolean makePublic);

    void uploadBlob(String objectName, InputStream inputStream, boolean makePublic, String contentType);

    void copyBlob(String sourceObjectName, String targetObjectName, boolean makePublic);

    void copyAllBlobs(String sourceContainerName, String prefix, String targetContainerName, String targetPrefix, boolean makePublic);

    void setStorage(Storage storage);

    void setContainerName(String containerName);

    boolean delete(String objectName);

    boolean deleteAllFilesInFolder(String folder);

}
