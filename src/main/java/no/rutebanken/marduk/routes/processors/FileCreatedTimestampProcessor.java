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

package no.rutebanken.marduk.routes.processors;

import no.rutebanken.marduk.repository.FileNameAndDigestIdempotentRepository;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.time.LocalDateTime;

import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILTERING_FILE_CREATED_TIMESTAMP;

public class FileCreatedTimestampProcessor implements Processor {
    FileNameAndDigestIdempotentRepository fileNameAndDigestIdempotentRepository;

    public FileCreatedTimestampProcessor(FileNameAndDigestIdempotentRepository fileNameAndDigestIdempotentRepository) {
        this.fileNameAndDigestIdempotentRepository = fileNameAndDigestIdempotentRepository;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String fileName = exchange.getIn().getHeader(FILE_NAME, String.class);
        LocalDateTime createdAt = fileNameAndDigestIdempotentRepository.getCreatedAt(fileName);
        if (createdAt != null) {
            exchange.getIn().setHeader(FILTERING_FILE_CREATED_TIMESTAMP, createdAt.toString());
        }
    }
}