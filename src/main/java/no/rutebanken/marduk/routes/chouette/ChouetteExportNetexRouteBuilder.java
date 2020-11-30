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
import no.rutebanken.marduk.routes.chouette.json.ActionReportWrapper;
import no.rutebanken.marduk.routes.chouette.json.Parameters;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.entur.pubsub.camel.EnturGooglePubSubConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static no.rutebanken.marduk.Constants.BLOBSTORE_MAKE_BLOB_PUBLIC;
import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_CHOUETTE;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_URL;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.JSON_PART;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Utils.getLastPathElementOfUrl;

@Component
public class ChouetteExportNetexRouteBuilder extends AbstractChouetteRouteBuilder {
    @Value("${chouette.url}")
    private String chouetteUrl;

    @Value("${chouette.export.days.forward:365}")
    private int daysForward;

    @Value("${chouette.export.days.back:365}")
    private int daysBack;

    @Value("${chouette.netex.export.stops:false}")
    private boolean exportStops;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("entur-google-pubsub:ChouetteExportNetexQueue").streamCaching()

                .log(LoggingLevel.INFO, getClass().getName(), "Starting Chouette Netex export for provider with id ${header." + PROVIDER_ID + "}")
                .process(e -> {
                    // Add correlation id only if missing
                    e.getIn().setHeader(Constants.CORRELATION_ID, e.getIn().getHeader(Constants.CORRELATION_ID, UUID.randomUUID().toString()));
                    e.getIn().removeHeader(Constants.CHOUETTE_JOB_ID);
                })
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX).state(JobEvent.State.PENDING).build())
                .to("direct:updateStatus")

                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .process(e -> e.getIn().setHeader(JSON_PART, Parameters.getDefaultNetexExportParameters(getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)), exportStops))) //Using header to addToExchange json data
                .log(LoggingLevel.DEBUG, correlation() + "Creating multipart request")
                .process(e -> toGenericChouetteMultipart(e))
                .setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"))
                .toD(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/exporter/netexprofile")
                .process(e -> {
                    e.getIn().setHeader(CHOUETTE_JOB_STATUS_URL, e.getIn().getHeader("Location", String.class));
                    e.getIn().setHeader(Constants.CHOUETTE_JOB_ID, getLastPathElementOfUrl(e.getIn().getHeader("Location", String.class)));
                })
                .setHeader(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION, constant("direct:processNetexExportResult"))
                .setHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE, constant(JobEvent.TimetableAction.EXPORT_NETEX.name()))
                .removeHeader("loopCounter")
                .setBody(constant(null))
                .to("entur-google-pubsub:ChouettePollStatusQueue")
                .routeId("chouette-start-export-netex");


        from("direct:processNetexExportResult")
                .choice()
                .when(simple("${header.action_report_result} == 'OK'"))
                .log(LoggingLevel.INFO, correlation() + "NeTEx export successful for provider ${header." + CHOUETTE_REFERENTIAL + "}. Downloading export data")
                .log(LoggingLevel.DEBUG, correlation() + "Downloading NeTEx export data from ${header.data_url}")
                .removeHeaders(Constants.CAMEL_ALL_HEADERS, EnturGooglePubSubConstants.ACK_ID)
                .setBody(simple(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http.HttpMethods.GET))
                .toD("${header.data_url}")

                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, simple("false", Boolean.class))
                .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_CHOUETTE + "netex/${header." + CHOUETTE_REFERENTIAL + "}-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME))
                .to("direct:uploadBlob")
                .setBody(constant(null))

                .to("entur-google-pubsub:ChouetteMergeWithFlexibleLinesQueue")
                .to("entur-google-pubsub:ChouetteExportGtfsQueue")
                .to("entur-google-pubsub:ChouetteExportNetexBlocksQueue")

                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX).state(JobEvent.State.OK).build())

                .endChoice()
                .when(simple("${header.action_report_result} == 'NOK'"))
                .process(e -> {
                            ActionReportWrapper actionReportWrapper = e.getIn().getBody(ActionReportWrapper.class);
                            if (actionReportWrapper != null && actionReportWrapper.actionReport != null && actionReportWrapper.actionReport.failure != null) {
                                ActionReportWrapper.Failure failure = actionReportWrapper.actionReport.failure;
                                if (JobEvent.CHOUETTE_JOB_FAILURE_CODE_NO_DATA_PROCEEDED.equals(failure.code)) {
                                    e.getIn().setHeader(Constants.JOB_ERROR_CODE, JobEvent.JOB_ERROR_NETEX_EXPORT_EMPTY);
                                }
                            }
                        }
                )
                .log(LoggingLevel.WARN, correlation() + "Netex export failed with error code ${header." + Constants.JOB_ERROR_CODE + "}")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX).state(JobEvent.State.FAILED).build())
                .otherwise()
                .log(LoggingLevel.ERROR, correlation() + "Something went wrong on Netex export")
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.EXPORT_NETEX).state(JobEvent.State.FAILED).build())
                .end()
                .to("direct:updateStatus")
                .removeHeader(Constants.CHOUETTE_JOB_ID)
                .routeId("chouette-process-export-netex-status");
    }


}
