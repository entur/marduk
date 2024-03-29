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

package no.rutebanken.marduk.routes.file;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.FileNameAndDigest;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

import java.io.InputStream;

import static no.rutebanken.marduk.Constants.FILE_APPLY_DUPLICATES_FILTER;
import static no.rutebanken.marduk.Constants.FILE_SKIP_STATUS_UPDATE_FOR_DUPLICATES;
import static org.apache.camel.builder.PredicateBuilder.and;
import static org.apache.camel.builder.PredicateBuilder.not;

@Component
public class IdempotentFileFilterRoute extends BaseRouteBuilder {

    private static final String HEADER_FILE_NAME_AND_DIGEST = "file_NameAndDigest";

    private final IdempotentRepository fileNameAndDigestIdempotentRepository;

    public IdempotentFileFilterRoute(IdempotentRepository fileNameAndDigestIdempotentRepository) {
        this.fileNameAndDigestIdempotentRepository = fileNameAndDigestIdempotentRepository;
    }

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:filterDuplicateFile").routeId("filter-duplicate-file")
                .onCompletion().onFailureOnly()
                // Need to set removeOnFailure=false and clean up ourselves if exchange fails. IdempotentConsumer impl removes key even if not added during failed exchange (because it already existed, ie is a duplicate).
                // We will only remove the key on failure if not a duplicate.
                .onWhen(and(exchangeProperty(Exchange.DUPLICATE_MESSAGE).isNotEqualTo(true), header(HEADER_FILE_NAME_AND_DIGEST).isNotNull()))
                    .choice().when(e -> fileNameAndDigestIdempotentRepository.remove(e.getIn().getHeader(HEADER_FILE_NAME_AND_DIGEST).toString()))
                        .log(LoggingLevel.INFO, "Removed from repository as exchange failed: ${exchangeId} with id: ${header." + HEADER_FILE_NAME_AND_DIGEST + "}")
                    .end()
                .end()
                .choice()
                .when(header(FILE_APPLY_DUPLICATES_FILTER).isEqualTo(true))
                .log(LoggingLevel.INFO, getClass().getName(), correlation() +  "Checking duplicate on file ${header." + Exchange.FILE_NAME + "}")
                .to("direct:runIdempotentConsumer")
                .endChoice();


        from("direct:runIdempotentConsumer")
                .choice()
                .when(header(Constants.FILE_APPLY_DUPLICATES_FILTER_ON_NAME_ONLY))
                // checking only duplicate file name
                .process(e -> e.getIn().setHeader(HEADER_FILE_NAME_AND_DIGEST, new FileNameAndDigest(e.getIn().getHeader(Exchange.FILE_NAME, String.class), DigestUtils.md5Hex(e.getIn().getHeader(Exchange.FILE_NAME, String.class)))))
                .otherwise()
                // checking both duplicate file name and duplicate binary content
                .process(e -> e.getIn().setHeader(HEADER_FILE_NAME_AND_DIGEST, new FileNameAndDigest(e.getIn().getHeader(Exchange.FILE_NAME, String.class), DigestUtils.md5Hex(e.getIn().getBody(InputStream.class)))))
                .end()
                .end()
                .idempotentConsumer(header(HEADER_FILE_NAME_AND_DIGEST)).idempotentRepository(fileNameAndDigestIdempotentRepository).skipDuplicate(false).removeOnFailure(false)
                .filter(exchangeProperty(Exchange.DUPLICATE_MESSAGE).isEqualTo(true))
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Detected ${header." + Exchange.FILE_NAME + "} as duplicate.")
                .to("direct:updateStatusForDuplicateFile")
                .stop()
                .end();


        from("direct:updateStatusForDuplicateFile")
                .choice().when(not(simple("${header." + FILE_SKIP_STATUS_UPDATE_FOR_DUPLICATES + "}")))
                .process( e -> e.getIn().setHeader(Constants.JOB_ERROR_CODE, JobEvent.JOB_ERROR_DUPLICATE_FILE))
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_TRANSFER).state(JobEvent.State.FAILED).build())
                .to("direct:updateStatus")
                .endChoice();

    }
}
