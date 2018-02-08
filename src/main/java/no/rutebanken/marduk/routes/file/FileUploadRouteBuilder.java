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
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.FileItemFactory;
import org.apache.tomcat.util.http.fileupload.UploadContext;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.*;

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
                .process(e -> convertBodyToFileItems(e))
                .split().body()
                .process(e -> e.getIn().setHeader(Constants.CORRELATION_ID, e.getIn().getHeader(Constants.CORRELATION_ID, UUID.randomUUID().toString())))
                .setHeader(FILE_NAME, simple("${body.name}"))
                .setHeader(FILE_HANDLE, simple("inbound/received/${header." + CHOUETTE_REFERENTIAL + "}/${header." + FILE_NAME + "}"))
                .process(e -> e.getIn().setHeader(FILE_CONTENT_HEADER, new CloseShieldInputStream(e.getIn().getBody(FileItem.class).getInputStream())))
                .to("direct:uploadFileAndStartImport")
                .routeId("files-upload");


        from("direct:uploadFileAndStartImport")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_TRANSFER).state(JobEvent.State.STARTED).build()).inOnly("direct:updateStatus")
                .doTry()
                .log(LoggingLevel.INFO, correlation() + "About to upload timetable file to blob store: ${header." + FILE_HANDLE + "}")
                .setBody(header(FILE_CONTENT_HEADER))
                .to("direct:uploadBlob")
                .log(LoggingLevel.INFO, correlation() + "Finished uploading timetable file to blob store: ${header." + FILE_HANDLE + "}")
                .setBody(constant(null))
                .inOnly("activemq:queue:ProcessFileQueue")
                .log(LoggingLevel.INFO, correlation() + "Triggered import pipeline for timetable file: ${header." + FILE_HANDLE + "}")
                .doCatch(Exception.class)
                .log(LoggingLevel.WARN, correlation() + "Upload of timetable data to blob store failed for file: ${header." + FILE_HANDLE + "}")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_TRANSFER).state(JobEvent.State.FAILED).build()).inOnly("direct:updateStatus")
                .end()
                .routeId("file-upload-and-start-import");
    }

    private void convertBodyToFileItems(Exchange e) {
        FileItemFactory factory = new DiskFileItemFactory();
        ServletFileUpload upload = new ServletFileUpload(factory);

        try {
            e.getIn().setBody(upload.parseRequest(new SimpleUploadContext(StandardCharsets.UTF_8, e.getIn().getHeader(Exchange.CONTENT_TYPE, String.class), IOUtils.toByteArray(e.getIn().getBody(InputStream.class)))));
        } catch (Exception ex) {
            throw new MardukException("Failed to parse multipart content: " + ex.getMessage());
        }

    }


    /**
     * Wrapper class for passing form multipart body to ServletFileUpload parser.
     */
    public class SimpleUploadContext implements UploadContext {
        private final Charset charset;
        private final String contentType;
        private final byte[] content;

        public SimpleUploadContext(Charset charset, String contentType, byte[] content) {
            this.charset = charset;
            this.contentType = contentType;
            this.content = content;
        }

        public String getCharacterEncoding() {
            return charset.displayName();
        }

        public String getContentType() {
            return contentType;
        }

        @Override
        public long contentLength() {
            return content.length;
        }

        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(content);
        }
    }
}
