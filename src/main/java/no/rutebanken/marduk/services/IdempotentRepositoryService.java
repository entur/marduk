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

import org.apache.camel.spi.IdempotentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IdempotentRepositoryService {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    IdempotentRepository fileNameAndDigestIdempotentRepository;

    public void cleanUniqueFileNameAndDigestRepo() {
        logger.info("Starting cleaning of unique file name and digest idempotent message repository.");
        fileNameAndDigestIdempotentRepository.clear();
        logger.info("Done cleaning unique file name and digest idempotent message repository.");
    }

}
