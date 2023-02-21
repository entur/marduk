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

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.springframework.stereotype.Component;

import javax.servlet.http.Part;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;

/**
 * Upload file to blob store and trigger import pipeline.
 */
@Component
public class FileUploadRouteBuilder extends BaseRouteBuilder {

    private static final String FILE_CONTENT_HEADER = "RutebankenFileContent";

    @Override
    public void configure() throws Exception {
        super.configure();


        from("direct:uploadFilesAndStartImport")
                .transacted()
                .setBody(simple("${exchange.getIn().getRequest().getParts()}"))
                .log(LoggingLevel.DEBUG, correlation() + "Received multipart request containing ${body.size()} parts")
                .split().body()
                .log(LoggingLevel.DEBUG, correlation() + "Processing part: name=${body.name}, submittedFileName=${body.submittedFileName}, size=${body.size}, contentType=${body.contentType}")
                .process(this::setCorrelationIdIfMissing)
                .setHeader(FILE_NAME, simple("${body.submittedFileName}"))
                .setHeader(FILE_HANDLE, simple("inbound/received/${header." + CHOUETTE_REFERENTIAL + "}/${header." + FILE_NAME + "}"))
                .process(e -> e.getIn().setHeader(FILE_CONTENT_HEADER, CloseShieldInputStream.wrap(e.getIn().getBody(Part.class).getInputStream())))
                .to("direct:uploadFileAndStartImport")
                .routeId("files-upload");


        from("direct:uploadFileAndStartImport").streamCaching()
                .transacted()
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_TRANSFER).state(JobEvent.State.STARTED).build()).to(ExchangePattern.InOnly, "direct:updateStatus")
                .doTry()
                .log(LoggingLevel.INFO, correlation() + "Uploading timetable file to blob store: ${header." + FILE_HANDLE + "}")
                .setBody(header(FILE_CONTENT_HEADER))
                .setHeader(Exchange.FILE_NAME, header(FILE_NAME))
                .to("direct:filterDuplicateFile")
                .to("direct:uploadBlob")
                .log(LoggingLevel.INFO, correlation() + "Finished uploading timetable file to blob store: ${header." + FILE_HANDLE + "}")
                .setBody(constant(""))
                .to(ExchangePattern.InOnly, "google-pubsub:{{marduk.pubsub.project.id}}:ProcessFileQueue")
                .log(LoggingLevel.INFO, correlation() + "Triggered import pipeline for timetable file: ${header." + FILE_HANDLE + "}")
                .doCatch(Exception.class)
                .log(LoggingLevel.WARN, correlation() + "Upload of timetable data to blob store failed for file: ${header." + FILE_HANDLE + "} (${exception.stacktrace})")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_TRANSFER).state(JobEvent.State.FAILED).build()).to(ExchangePattern.InOnly, "direct:updateStatus")
                .end()
                .routeId("file-upload-and-start-import");
    }
}
