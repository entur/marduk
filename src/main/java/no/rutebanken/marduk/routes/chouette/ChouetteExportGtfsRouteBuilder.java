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

package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.chouette.json.Parameters;
import no.rutebanken.marduk.gtfs.GtfsFileUtils;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEvent.State;
import no.rutebanken.marduk.routes.status.JobEvent.TimetableAction;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.BLOBSTORE_MAKE_BLOB_PUBLIC;
import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_URL;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.JSON_PART;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Utils.getLastPathElementOfUrl;

/**
 * Exports gtfs files from Chouette
 */
@Component
public class ChouetteExportGtfsRouteBuilder extends AbstractChouetteRouteBuilder {

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Value("${chouette.export.days.forward:365}")
    private int daysForward;

    @Value("${chouette.export.days.back:365}")
    private int daysBack;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("entur-google-pubsub:ChouetteExportGtfsQueue").streamCaching()

                .log(LoggingLevel.INFO, getClass().getName(), "Starting Chouette GTFS export for provider with id ${header." + PROVIDER_ID + "}")
                .process(e -> {
                    // Add correlation id only if missing
                    e.getIn().setHeader(Constants.CORRELATION_ID, e.getIn().getHeader(Constants.CORRELATION_ID, UUID.randomUUID().toString()));
                    e.getIn().removeHeader(Constants.CHOUETTE_JOB_ID);
                })
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.EXPORT).state(State.PENDING).build())
                .to("direct:updateStatus")

                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .process(e -> e.getIn().setHeader(JSON_PART, Parameters.getGtfsExportParameters(getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class))))) //Using header to addToExchange json data
                .log(LoggingLevel.INFO, correlation() + "Creating multipart request")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> toGenericChouetteMultipart(e))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .toD(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/exporter/gtfs")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .process(e -> {
                    e.getIn().setHeader(CHOUETTE_JOB_STATUS_URL, e.getIn().getHeader("Location", String.class).replaceFirst("http", "http4"));
                    e.getIn().setHeader(Constants.CHOUETTE_JOB_ID, getLastPathElementOfUrl(e.getIn().getHeader("Location", String.class)));
                })
                .setHeader(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION, constant("direct:processExportResult"))
                .setHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE, constant(JobEvent.TimetableAction.EXPORT.name()))
                .removeHeader("loopCounter")
                .setBody(constant(null))
                .to("entur-google-pubsub:ChouettePollStatusQueue")
                .routeId("chouette-send-export-job");


        from("direct:processExportResult")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .choice()
                .when(simple("${header.action_report_result} == 'OK'"))
                .log(LoggingLevel.INFO, correlation() + "Export ended with status '${header.action_report_result}'")
                .log(LoggingLevel.INFO, correlation() + "Calling url ${header.data_url}")
                .removeHeaders("Camel*","CamelGooglePubsub.MsgAckId")
                .setBody(simple(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .toD("${header.data_url}")
                .to("direct:addGtfsFeedInfo")
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(true))
                .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + "gtfs/${header." + CHOUETTE_REFERENTIAL + "}-" + Constants.CURRENT_AGGREGATED_GTFS_FILENAME))
                .to("direct:uploadBlob")
                .inOnly("entur-google-pubsub:GtfsExportMergedQueue")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.EXPORT).state(State.OK).build())
                .when(simple("${header.action_report_result} == 'NOK'"))
                .log(LoggingLevel.WARN, correlation() + "Export failed")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.EXPORT).state(State.FAILED).build())
                .otherwise()
                .log(LoggingLevel.ERROR, correlation() + "Something went wrong on export")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(TimetableAction.EXPORT).state(State.FAILED).build())
                .end()
                .to("direct:updateStatus")
                .routeId("chouette-process-export-status");

        from("direct:addGtfsFeedInfo")
                .log(LoggingLevel.INFO, correlation() + "Adding feed_info.txt to GTFS file")
                .process(e -> e.getIn().setBody(GtfsFileUtils.addOrReplaceFeedInfo(e.getIn().getBody(InputStream.class))))
                .routeId("chouette-process-export-gtfs-feedinfo");
    }

}
