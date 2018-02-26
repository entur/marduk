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

import no.rutebanken.marduk.domain.FileNameAndDigest;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;

import static no.rutebanken.marduk.Constants.FILE_SKIP_STATUS_UPDATE_FOR_DUPLICATES;
import static org.apache.camel.builder.PredicateBuilder.and;
import static org.apache.camel.builder.PredicateBuilder.not;

@Component
public class IdempotentFileFilterRoute extends BaseRouteBuilder {

    @Autowired
    private IdempotentRepository fileNameAndDigestIdempotentRepository;


    private static final String HEADER_FILE_NAME_AND_DIGEST = "file_NameAndDigest";

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
                .when(simple("{{idempotent.skip:false}}"))
                .log(LoggingLevel.WARN, getClass().getName(), "Idempotent filter is disabled. This also means that consumed SFTP files will be deleted.")
                .otherwise()
                .to("direct:runIdempotentConsumer")
                .endChoice();


        from("direct:runIdempotentConsumer")
                .process(e -> e.getIn().setHeader(HEADER_FILE_NAME_AND_DIGEST, new FileNameAndDigest(e.getIn().getHeader(Exchange.FILE_NAME, String.class), DigestUtils.md5Hex(e.getIn().getBody(InputStream.class)))))
                .idempotentConsumer(header(HEADER_FILE_NAME_AND_DIGEST)).messageIdRepository(fileNameAndDigestIdempotentRepository).skipDuplicate(false).removeOnFailure(false)
                .filter(exchangeProperty(Exchange.DUPLICATE_MESSAGE).isEqualTo(true))
                .log(LoggingLevel.DEBUG, getClass().getName(), "Detected ${header." + Exchange.FILE_NAME + "} as duplicate.")
                .to("direct:updateStatusForDuplicateFile")
                .stop()
                .end();


        from("direct:updateStatusForDuplicateFile")
                .choice().when(not(simple("${header." + FILE_SKIP_STATUS_UPDATE_FOR_DUPLICATES + "}")))
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_TRANSFER).state(JobEvent.State.DUPLICATE).build()).to("direct:updateStatus").endChoice();

    }
}
