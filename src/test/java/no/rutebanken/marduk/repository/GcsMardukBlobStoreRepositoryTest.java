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

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GcsMardukBlobStoreRepositoryTest {

    private static final String CONTAINER = "graphs";

    private final Storage storage = mock(Storage.class);

    private GcsMardukBlobStoreRepository repository;

    @BeforeEach
    void setUp() {
        repository = new GcsMardukBlobStoreRepository(storage, mock(ProviderRepository.class));
        repository.setContainerName(CONTAINER);
    }

    @Test
    void getBlobContentDownloadsTheBlob() {
        byte[] content = "{}".getBytes(StandardCharsets.UTF_8);
        when(storage.readAllBytes(BlobId.of(CONTAINER, "netex-otp2/current-otp2"))).thenReturn(content);

        assertArrayEquals(content, repository.getBlobContent("netex-otp2/current-otp2"));
    }

    @Test
    void getBlobContentOfAMissingBlobIsNull() {
        when(storage.readAllBytes(BlobId.of(CONTAINER, "missing"))).thenThrow(new StorageException(404, "Not Found"));

        assertNull(repository.getBlobContent("missing"));
    }

    @Test
    void getBlobContentPropagatesTheOtherFailures() {
        StorageException failure = new StorageException(503, "Service Unavailable");
        when(storage.readAllBytes(BlobId.of(CONTAINER, "unavailable"))).thenThrow(failure);

        assertSame(failure, assertThrows(StorageException.class, () -> repository.getBlobContent("unavailable")));
    }
}
