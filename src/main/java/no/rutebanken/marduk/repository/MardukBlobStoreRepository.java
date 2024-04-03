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
import org.rutebanken.helper.gcp.repository.BlobStoreRepository;

import java.util.Collection;

/**
 * Repository interface for managing binary files.
 * The main implementation {@link GcsMardukBlobStoreRepository} targets Google Cloud Storage.
 * A simple implementation {@link LocalDiskMardukBlobStoreRepository} is available for testing in a local environment.
 */
public interface MardukBlobStoreRepository  extends BlobStoreRepository {

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


}
