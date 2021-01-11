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

import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.routes.TransactionalBaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.UploadContext;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;

/**
 * Upload file to blob store and trigger import pipeline.
 */
@Component
public class FileUploadRouteBuilder extends TransactionalBaseRouteBuilder {

    private static final String FILE_CONTENT_HEADER = "RutebankenFileContent";

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUploadRouteBuilder.class);

    @Override
    public void configure() {
        super.configure();


        from("direct:uploadFilesAndStartImport")
                .process(this::convertBodyToFileItems)
                .split().body()
                .process(this::setCorrelationIdIfMissing)
                .setHeader(FILE_NAME, simple("${body.name}"))
                .setHeader(FILE_HANDLE, simple("inbound/received/${header." + CHOUETTE_REFERENTIAL + "}/${header." + FILE_NAME + "}"))
                .process(e -> e.getIn().setHeader(FILE_CONTENT_HEADER, new CloseShieldInputStream(e.getIn().getBody(FileItem.class).getInputStream())))
                .to("direct:uploadFileAndStartImport")
                .routeId("files-upload");


        from("direct:uploadFileAndStartImport").streamCaching()
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_TRANSFER).state(JobEvent.State.STARTED).build()).to(ExchangePattern.InOnly, "direct:updateStatus")
                .doTry()
                .log(LoggingLevel.INFO, correlation() + "Uploading timetable file to blob store: ${header." + FILE_HANDLE + "}")
                .setBody(header(FILE_CONTENT_HEADER))
                .setHeader(Exchange.FILE_NAME, header(FILE_NAME))
                .to("direct:filterDuplicateFile")
                .to("direct:uploadBlob")
                .log(LoggingLevel.INFO, correlation() + "Finished uploading timetable file to blob store: ${header." + FILE_HANDLE + "}")
                .setBody(constant(null))
                .to(ExchangePattern.InOnly, "google-pubsub:{{spring.cloud.gcp.pubsub.project-id}}:ProcessFileQueue")
                .log(LoggingLevel.INFO, correlation() + "Triggered import pipeline for timetable file: ${header." + FILE_HANDLE + "}")
                .doCatch(Exception.class)
                .log(LoggingLevel.WARN, correlation() + "Upload of timetable data to blob store failed for file: ${header." + FILE_HANDLE + "}")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_TRANSFER).state(JobEvent.State.FAILED).build()).to(ExchangePattern.InOnly, "direct:updateStatus")
                .end()
                .routeId("file-upload-and-start-import");
    }

    private void convertBodyToFileItems(Exchange e) {
        FileItemFactory factory = new DiskFileItemFactory();
        ServletFileUpload upload = new ServletFileUpload(factory);

        try {
            byte[] content = IOUtils.toByteArray(e.getIn().getBody(InputStream.class));
            String contentType = e.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
            LOGGER.debug("Received a multipart request (size: {} bytes) with content type {} ", content.length, contentType);
            SimpleUploadContext uploadContext = new SimpleUploadContext(StandardCharsets.UTF_8, contentType, content);
            List<FileItem> fileItems = upload.parseRequest(uploadContext);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("The multipart request contains {} file(s)", fileItems.size());
                for (FileItem fileItem : fileItems) {
                    LOGGER.debug("Received file {} (size: {})", fileItem.getName(), fileItem.getSize());
                }
            }
            e.getIn().setBody(fileItems);
        } catch (Exception ex) {
            throw new MardukException("Failed to parse multipart content: " + ex.getMessage());
        }

    }


    /**
     * Wrapper class for passing form multipart body to ServletFileUpload parser.
     */
    public static class SimpleUploadContext implements UploadContext {
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

        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public int getContentLength() {
            return content.length;
        }
    }
}
