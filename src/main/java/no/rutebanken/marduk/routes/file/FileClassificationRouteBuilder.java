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
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.beans.FileTypeClassifierBean;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ValidationException;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILE_TYPE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

/**
 * Receives file handle, pulls file from blob store, classifies files and performs initial validation.
 */
@Component
public class FileClassificationRouteBuilder extends BaseRouteBuilder {

    /**
     * Message header for sending the dataset codespace to Antu.
     */
    private static final String DATASET_CODESPACE = "EnturDatasetCodespace";

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

                .when(header(FILE_TYPE).isEqualTo(FileType.UNKNOWN_FILE_EXTENSION.name()))
                .log(LoggingLevel.WARN, correlation() + "The file ${header." + FILE_HANDLE + "} does not end with a .zip or .ZIP extension")
                .process( e -> e.getIn().setHeader(Constants.JOB_ERROR_CODE, JobEvent.JOB_ERROR_FILE_UNKNOWN_FILE_EXTENSION))
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_CLASSIFICATION).state(JobEvent.State.FAILED).build())
                .to("direct:updateStatus")
                .stop()

                .when(header(FILE_TYPE).isEqualTo(FileType.UNKNOWN_FILE_TYPE.name()))
                .log(LoggingLevel.WARN, correlation() + "The file ${header." + FILE_HANDLE + "} cannot be processed: unknown file type")
                .process( e -> e.getIn().setHeader(Constants.JOB_ERROR_CODE, JobEvent.JOB_ERROR_UNKNOWN_FILE_TYPE))
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_CLASSIFICATION).state(JobEvent.State.FAILED).build())
                .to("direct:updateStatus")
                .stop()

                .when(header(FILE_TYPE).isEqualTo(FileType.NOT_A_ZIP_FILE.name()))
                .log(LoggingLevel.WARN, correlation() + "The file ${header." + FILE_HANDLE + "} is not a valid zip archive")
                .process( e -> e.getIn().setHeader(Constants.JOB_ERROR_CODE, JobEvent.JOB_ERROR_FILE_NOT_A_ZIP_FILE))
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_CLASSIFICATION).state(JobEvent.State.FAILED).build())
                .to("direct:updateStatus")
                .stop()

                .when(header(FILE_TYPE).isEqualTo(FileType.ZIP_CONTAINS_SUBDIRECTORIES.name()))
                .log(LoggingLevel.WARN, correlation() + "The file ${header." + FILE_HANDLE + "} contains one or more subdirectories")
                .process( e -> e.getIn().setHeader(Constants.JOB_ERROR_CODE, JobEvent.JOB_ERROR_FILE_ZIP_CONTAINS_SUB_DIRECTORIES))
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_CLASSIFICATION).state(JobEvent.State.FAILED).build())
                .to("direct:updateStatus")
                .stop()

                .when(header(FILE_TYPE).isEqualTo(FileType.INVALID_ZIP_FILE_ENTRY_CONTENT_ENCODING.name()))
                .log(LoggingLevel.WARN, correlation() + "The file ${header." + FILE_HANDLE + "} contains one or more invalid XML files: invalid encoding")
                .process( e -> e.getIn().setHeader(Constants.JOB_ERROR_CODE, JobEvent.JOB_ERROR_INVALID_XML_ENCODING))
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_CLASSIFICATION).state(JobEvent.State.FAILED).build())
                .to("direct:updateStatus")
                .stop()

                .when(header(FILE_TYPE).isEqualTo(FileType.INVALID_ZIP_FILE_ENTRY_XML_CONTENT.name()))
                .log(LoggingLevel.WARN, correlation() + "The file ${header." + FILE_HANDLE + "} contains one or more invalid XML files: unparseable XML")
                .process( e -> e.getIn().setHeader(Constants.JOB_ERROR_CODE, JobEvent.JOB_ERROR_INVALID_XML_CONTENT))
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_CLASSIFICATION).state(JobEvent.State.FAILED).build())
                .to("direct:updateStatus")
                .stop()

                .when(header(FILE_TYPE).isEqualTo(FileType.INVALID_ZIP_FILE_ENTRY_NAME_ENCODING.name()))
                .log(LoggingLevel.WARN, correlation() + "The file ${header." + FILE_HANDLE + "} contains one or more invalid zip entry names: invalid encoding")
                .process( e -> e.getIn().setHeader(Constants.JOB_ERROR_CODE, JobEvent.JOB_ERROR_INVALID_ZIP_ENTRY_ENCODING))
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_CLASSIFICATION).state(JobEvent.State.FAILED).build())
                .to("direct:updateStatus")
                .stop()

                .when(header(FILE_TYPE).isEqualTo(FileType.INVALID_FILE_NAME.name()))
                .log(LoggingLevel.WARN, correlation() + "File with invalid characters in file name ${header." + FILE_HANDLE + "}")
                .to("direct:sanitizeFileName")

                .otherwise()
                .log(LoggingLevel.INFO, correlation() + "Posting " + FILE_HANDLE + " ${header." + FILE_HANDLE + "} and " + FILE_TYPE + " ${header." + FILE_TYPE + "} on chouette import queue.")
                .setBody(simple(""))   //remove file data from body since this is in blobstore
                .to("entur-google-pubsub:ChouetteImportQueue")
                .to("direct:antuNetexValidation")
                .end()
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_CLASSIFICATION).state(JobEvent.State.OK).build()).to("direct:updateStatus")
                .routeId("file-classify");

        from("direct:sanitizeFileName")
                .process(e -> {
                    String originalFileName = e.getIn().getHeader(Constants.FILE_NAME, String.class);
                    String sanitizedFileName = MardukFileUtils.sanitizeFileName(originalFileName);
                    e.getIn().setHeader(FILE_HANDLE, Constants.BLOBSTORE_PATH_INBOUND
                                                             + getProviderRepository().getReferential(e.getIn().getHeader(PROVIDER_ID, Long.class))
                                                             + "/" + sanitizedFileName);
                    e.getIn().setHeader(FILE_NAME, sanitizedFileName);
                })
                .log(LoggingLevel.INFO, correlation() + "Uploading file with new file name ${header." + FILE_HANDLE + "}")
                .to("direct:uploadBlob")
                .to("entur-google-pubsub:ProcessFileQueue")
                .routeId("file-sanitize-filename");

        from("direct:antuNetexValidation")
                .filter(header(FILE_TYPE).isEqualTo(FileType.NETEXPROFILE))
                .process(e -> {
                    Provider provider = getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class));
                    e.getIn().setHeader(DATASET_CODESPACE, provider.chouetteInfo.referential);
                })
                .to("entur-google-pubsub:AntuNetexValidationQueue")
                .routeId("antu-netex-validation");
    }

}
