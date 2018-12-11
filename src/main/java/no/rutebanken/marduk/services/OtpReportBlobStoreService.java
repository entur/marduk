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

import com.google.cloud.storage.Storage;
import no.rutebanken.marduk.repository.BlobStoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.InputStream;

@Service
public class OtpReportBlobStoreService {

    @Autowired
    private BlobStoreRepository repository;

    @Autowired
    private Storage otpReportStorage;

    @Value("${blobstore.gcs.otpreport.container.name}")
    private String containerName;

    @PostConstruct
    public void init() {
        repository.setStorage(otpReportStorage);
        repository.setContainerName(containerName);
    }

    public void uploadBlob(String name, InputStream inputStream, boolean makePublic) {
        if (name.endsWith(".html")) {
            repository.uploadBlob(name, inputStream, makePublic, "text/html");
        } else {
            repository.uploadBlob(name, inputStream, makePublic);
        }
    }

}
