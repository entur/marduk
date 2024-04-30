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

package no.rutebanken.marduk.services;

import no.rutebanken.marduk.repository.BlobStoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Operations on blobs in the Marduk exchange bucket.
 */

@Service
public class ExchangeBlobStoreService extends AbstractBlobStoreService {

    public ExchangeBlobStoreService(@Value("${blobstore.gcs.exchange.container.name}") String containerName, @Autowired BlobStoreRepository repository) {
        super(containerName, repository);
    }

}
