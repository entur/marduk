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
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.beans.FileTypeClassifierBean;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ValidationException;
import org.apache.commons.codec.CharEncoding;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILE_TYPE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

/**
 * Receives file handle, pulls file from blob store, classifies files and performs initial validation.
 */
@Component
public class FileClassificationRouteBuilder extends BaseRouteBuilder {

    @Override
    public void configure() throws Exception {
        super.configure();

        onException(ValidationException.class)
                .handled(true)
                .log(LoggingLevel.INFO, correlation() + "Could not process file ${header." + FILE_HANDLE + "}")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_CLASSIFICATION).state(JobEvent.State.FAILED).build())
                .to("direct:updateStatus")
                .setBody(simple(""))      //remove file data from body
                .to("entur-google-pubsub:DeadLetterQueue");

        from("entur-google-pubsub:ProcessFileQueue")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_TRANSFER).state(JobEvent.State.OK).build())
                .to("direct:updateStatus")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_CLASSIFICATION).state(JobEvent.State.STARTED).build()).to("direct:updateStatus")
                .to("direct:getBlob")
                .convertBodyTo(byte[].class)
                .validate().method(FileTypeClassifierBean.class, "validateFile")
                .choice()
                .when(header(FILE_TYPE).isEqualTo(FileType.INVALID_FILE_NAME.name()))
                .log(LoggingLevel.WARN, correlation() + "File with invalid characters in file name ${header." + FILE_HANDLE + "}")
                .to("direct:sanitizeFileName")
                .when(header(FILE_TYPE).isEqualTo(FileType.ZIP_WITH_SINGLE_FOLDER.name()))
                .log(LoggingLevel.WARN, correlation() + "Archive containing a single folder ${header." + FILE_HANDLE + "}")
                .to("direct:repackZipFile")
                .otherwise()
                .log(LoggingLevel.INFO, correlation() + "Posting " + FILE_HANDLE + " ${header." + FILE_HANDLE + "} and " + FILE_TYPE + " ${header." + FILE_TYPE + "} on chouette import queue.")
                .setBody(simple(""))   //remove file data from body since this is in blobstore
                .to("entur-google-pubsub:ChouetteImportQueue")
                .end()
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_CLASSIFICATION).state(JobEvent.State.OK).build()).to("direct:updateStatus")
                .routeId("file-classify");

        from("direct:repackZipFile")
                .bean(method(ZipFileUtils.class, "rePackZipFile"))
                .log(LoggingLevel.INFO, correlation() + "ZIP-file repacked ${header." + FILE_HANDLE + "}")
                .to("direct:uploadBlob")
                .to("entur-google-pubsub:ProcessFileQueue")
                .routeId("file-repack-zip");

        from("direct:sanitizeFileName")
                .process(e -> {
                    String orgFileName = e.getIn().getHeader(Constants.FILE_NAME, String.class);
                    String sanitizedFileName = sanitizeString(orgFileName);
                    e.getIn().setHeader(FILE_HANDLE, Constants.BLOBSTORE_PATH_INBOUND
                                                             + getProviderRepository().getReferential(e.getIn().getHeader(PROVIDER_ID, Long.class))
                                                             + "/" + sanitizedFileName);
                    e.getIn().setHeader(FILE_NAME, sanitizedFileName);
                })
                .log(LoggingLevel.INFO, correlation() + "Uploading file with new file name ${header." + FILE_HANDLE + "}")
                .to("direct:uploadBlob")
                .to("entur-google-pubsub:ProcessFileQueue")
                .routeId("file-sanitize-filename");
    }

    /**
     * Remove any non ISO_8859_1 characters from file name as these cause chouette import to crash
     */
    String sanitizeString(String value) {
        StringBuilder result = new StringBuilder();
        for (char val : value.toCharArray()) {

            if (Charset.forName(CharEncoding.ISO_8859_1).newEncoder().canEncode(val)) result.append(val);
        }
        return result.toString();
    }

}
