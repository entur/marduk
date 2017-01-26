package no.rutebanken.marduk.routes.file;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.Status;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;

import static no.rutebanken.marduk.Constants.FILE_SKIP_STATUS_UPDATE_FOR_DUPLICATES;
import static org.apache.camel.builder.PredicateBuilder.not;

@Component
public class IdempotentFileFilterRoute extends BaseRouteBuilder {

    @Autowired
    private IdempotentRepository digestIdempotentRepository;

    @Autowired
    private IdempotentRepository fileNameIdempotentRepository;


    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:filterDuplicateFile").routeId("filter-duplicate-file")
                .choice()
                    .when(simple("{{idempotent.skip:false}}"))
                    .log(LoggingLevel.WARN, getClass().getName(), "Idempotent filter is disabled. This also means that consumed SFTP files will be deleted.")
                .otherwise()
                   .to("direct:runIdempotentConsumer")
                .endChoice();


        from("direct:runIdempotentConsumer")
         .process(e -> e.getIn().setHeader("file_digest", DigestUtils.md5Hex(e.getIn().getBody(InputStream.class))))
                .idempotentConsumer(simple("${header.file_digest}")).messageIdRepository(digestIdempotentRepository).skipDuplicate(false)
                .filter(exchangeProperty(Exchange.DUPLICATE_MESSAGE).isEqualTo(true))
                .log(LoggingLevel.DEBUG, getClass().getName(), "Detected ${header." + Exchange.FILE_NAME + "} as duplicate based on digest.")
                .to("direct:updateStatusForDuplicateFile")
                .stop()
                .end()
                .idempotentConsumer(header(Exchange.FILE_NAME)).messageIdRepository(fileNameIdempotentRepository).skipDuplicate(false)
                .filter(exchangeProperty(Exchange.DUPLICATE_MESSAGE).isEqualTo(true))
                .log(LoggingLevel.WARN, getClass().getName(), "Detected ${header." + Exchange.FILE_NAME + "} as duplicate based on file name.")
                .to("direct:updateStatusForDuplicateFile")
                .stop()
                .end();


        from("direct:updateStatusForDuplicateFile")
                .choice().when(not(simple ("${header."+ FILE_SKIP_STATUS_UPDATE_FOR_DUPLICATES +"}")))
                .process(e -> Status.builder(e).action(Status.Action.FILE_TRANSFER).state(Status.State.DUPLICATE).build()).to("direct:updateStatus").endChoice();

    }
}
