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

    /**
     * Retrieve the list of files in the blob store under the given prefix.
     * @param prefix the prefix under which to look up files in the blob store.
     * @return The files in the blob store under the given prefix.
     */
    BlobStoreFiles listBlobs(String prefix);

    /**
     * Retrieve the list of files in the blob store under the given prefix. In the returned list
     * of {@link BlobStoreFiles.File}s, the file names are stripped of the prefix. Example:
     * /inbound/received/myfile.zip --> myfile.zip
     * @param prefix the prefix under which to look up files in the blob store.
     * @return The files in the blob store under the given prefix. The prefix is removed from the file names.
     */
    BlobStoreFiles listBlobsFlat(String prefix);

    InputStream getBlob(String objectName);

    /**
     * Upload a blob and returns its generation number
     * @param objectName the name of the blob in GCS
     * @param inputStream the blob data
     * @param makePublic makes the blob publicly accessible
     * @return the blob generation
     */
    long uploadBlob(String objectName, InputStream inputStream, boolean makePublic);

    /**
     * Upload a blob and returns its generation number
     * @param objectName the name of the blob in GCS
     * @param inputStream the blob data
     * @param makePublic makes the blob publicly accessible
     * @param contentType the blob content type
     * @return the blob generation
     */
    long uploadBlob(String objectName, InputStream inputStream, boolean makePublic, String contentType);

    void copyBlob(String sourceContainerName, String sourceObjectName, String targetContainerName, String targetObjectName, boolean makePublic);

    void copyBlob(String sourceContainerName, String sourceObjectName, Long sourceVersion, String targetContainerName, String targetObjectName, boolean makePublic);

    void copyAllBlobs(String sourceContainerName, String prefix, String targetContainerName, String targetPrefix, boolean makePublic);

    void setContainerName(String containerName);

    boolean delete(String objectName);

    boolean deleteAllFilesInFolder(String folder);

}
