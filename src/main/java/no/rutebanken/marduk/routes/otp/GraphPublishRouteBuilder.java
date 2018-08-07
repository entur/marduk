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

package no.rutebanken.marduk.routes.otp;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.Utils;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.services.OtpReportBlobStoreService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Date;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.GRAPH_OBJ;
import static no.rutebanken.marduk.Constants.METADATA_DESCRIPTION;
import static no.rutebanken.marduk.Constants.METADATA_FILE;
import static no.rutebanken.marduk.Constants.TIMESTAMP;
import static org.apache.camel.Exchange.FILE_PARENT;
import static org.apache.commons.io.FileUtils.deleteDirectory;

@Component
public class GraphPublishRouteBuilder extends BaseRouteBuilder {

    @Value("${otp.graph.deployment.notification.url:none}")
    private String otpGraphDeploymentNotificationUrl;

    @Value("${otp.graph.build.directory}")
    private String otpGraphBuildDirectory;

    @Value("${otp.graph.blobstore.subdirectory:graphs}")
    private String blobStoreSubdirectory;

    @Value("${otp.graph.blobstore.public.report.folder:report}")
    private String blobStorePublicReportFolder;

    @Value("${blobstore.gcs.otpreport.container.name}")
    String otpReportContainerName;

    @Autowired
    private OtpReportBlobStoreService otpReportBlobStoreService;

    private static final String ETCD_GRAPH_URL_SOURCE =  "/v2/keys/prod/marduk/etcd.graph.notification.url";


    private static final String GRAPH_VERSION = "RutebankenGraphVersion";

    @Override
    public void configure() throws Exception {
        super.configure();

        from("file:" + otpGraphBuildDirectory + "?fileName=" + GRAPH_OBJ + "&doneFileName=" + GRAPH_OBJ + ".done&recursive=true&noop=true")
                .log(LoggingLevel.INFO, "Starting graph publishing.")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .convertBodyTo(InputStream.class)
                .process(e -> {
                            e.getIn().setHeader(FILE_HANDLE, blobStoreSubdirectory + "/" + Utils.getOtpVersion() + "/" + e.getIn().getHeader(Exchange.FILE_NAME, String.class).replace("/", "-"));
                            String timestamp = getBuildTimestamp(e);
                            e.setProperty(TIMESTAMP, timestamp);
                            e.setProperty(GRAPH_VERSION, Utils.getOtpVersion() + "/" + timestamp + "-report");
                        }
                )
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .to("direct:uploadBlob")
                .log(LoggingLevel.INFO,  "Done uploading new OTP graph.")
                .to("direct:uploadVersionedGraphBuildReport")
                .to("direct:updateCurrentGraphReportVersion")
                .log(LoggingLevel.INFO,  "Done uploading OTP graph build reports.")
                .setProperty(Exchange.FILE_PARENT, header(Exchange.FILE_PARENT))
                .to("direct:notify")
                .to("direct:notifyEtcd")
                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GRAPH).action("BUILD_GRAPH").state(JobEvent.State.OK).correlationId(e.getProperty(TIMESTAMP, String.class)).build()).to("direct:updateStatus")
                .to("direct:cleanUp")
                .routeId("otp-graph-upload");

        from("direct:uploadVersionedGraphBuildReport")
                .split().exchange(e -> listReportFiles(e))
                .process(e ->
                        {
                            String fileName = e.getIn().getBody(File.class).getName();
                            otpReportBlobStoreService.uploadBlob(e.getProperty(GRAPH_VERSION) + "/" + fileName, e.getIn().getBody(InputStream.class), true);
                        }
                )
                .routeId("otp-graph-build-report-versioned-upload");

        from("direct:updateCurrentGraphReportVersion")
                .process(e ->
                                 otpReportBlobStoreService.uploadBlob("index.html", createRedirectPage(e.getProperty(GRAPH_VERSION, String.class)), true))
                .routeId("otp-graph-report-update-current");

        from("direct:notify")
                .setProperty("notificationUrl", constant(otpGraphDeploymentNotificationUrl))
                .choice()
                .when(exchangeProperty("notificationUrl").isNotEqualTo("none"))
                .log(LoggingLevel.INFO, getClass().getName(),  "Notifying " + otpGraphDeploymentNotificationUrl + " about new otp graph.")
                .setHeader(METADATA_DESCRIPTION, constant("Uploaded new Graph object file."))
                .setHeader(METADATA_FILE, simple("${header." + FILE_HANDLE + "}"))
                .setProperty("fileNameRetainer", simple("${header." + FILE_HANDLE + "}"))
                .process(e -> e.getIn().setBody(new Metadata("Uploaded new Graph object file.", e.getIn().getHeader(FILE_HANDLE, String.class), new Date(), Metadata.Status.OK, Metadata.Action.OTP_GRAPH_UPLOAD).getJson()))
                .removeHeaders("*")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .toD("${property.notificationUrl}")
                .log(LoggingLevel.DEBUG, getClass().getName(),  "Done notifying. Got a ${header." + Exchange.HTTP_RESPONSE_CODE + "} back.")
                .process(e -> e.getOut().setHeader(FILE_HANDLE, e.getProperty("fileNameRetainer")))
                .log(LoggingLevel.DEBUG, getClass().getName(),  "Put the retained file name header back, as it is going to be used later.")
                .otherwise()
                .log(LoggingLevel.WARN, getClass().getName(),  "No notification url configured for otp graph building. Doing nothing.")
                .routeId("otp-graph-notify");

        /* Putting value directly into etcd */
        from("direct:notifyEtcd")
                .setHeader(Constants.ETCD_KEY, simple(ETCD_GRAPH_URL_SOURCE))
                .to("direct:getEtcdValue")
                .process(e -> e.setProperty("notificationUrl", e.getIn().getBody() == null ? "none" : e.getIn().getBody(String.class)))
                .choice()
                .when(exchangeProperty("notificationUrl").isNotEqualTo("none"))
                .log(LoggingLevel.INFO, getClass().getName(),  "Notifying " + exchangeProperty("notificationUrl") + " about new otp graph.")
                .process(e -> e.getIn().setBody("value=" + e.getIn().getHeader(FILE_HANDLE, String.class)))
                .removeHeaders("*")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.PUT))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/x-www-form-urlencoded"))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .toD("${property.notificationUrl}")
                .log(LoggingLevel.INFO, getClass().getName(),  "Done notifying. Got a ${header." + Exchange.HTTP_RESPONSE_CODE + "} back.")
                .otherwise()
                .log(LoggingLevel.WARN, getClass().getName(),  "No notification url configured for etcd endpoint. Doing nothing.")
                .routeId("otp-graph-notify-etcd");


        from("direct:cleanUp")
                .log(LoggingLevel.DEBUG, getClass().getName(),  "Deleting build folder ${property." + Exchange.FILE_PARENT + "} ...")
                .process(e -> deleteDirectory(new File(e.getIn().getExchange().getProperty(Exchange.FILE_PARENT, String.class))))
                .log(LoggingLevel.DEBUG, getClass().getName(),  "Build folder ${property." + Exchange.FILE_PARENT + "} cleanup done.")
                .routeId("otp-graph-cleanup");
    }

    private String getBuildTimestamp(Exchange e) {
        return e.getIn().getHeader(Exchange.FILE_NAME, String.class).replace("/", "-").replace("-" + GRAPH_OBJ, "");
    }

    private Collection<File> listReportFiles(Exchange e) {
        String directory = e.getIn().getHeader(FILE_PARENT, String.class) + "/report";
        return FileUtils.listFiles(new File(directory), null, true);
    }

    private InputStream createRedirectPage(String version) {
        try {
            String url = "http://" + otpReportContainerName + "/" + version + "/index.html";
            String html = "<html>\n" +
                                  "<head>\n" +
                                  "    <meta http-equiv=\"refresh\" content=\"0; url=" + url + "\" />\n" +
                                  "</head>\n" +
                                  "</html>";

            return IOUtils.toInputStream(html, "UTF-8");
        } catch (IOException ioE) {
            throw new MardukException("Failed to create input stream for redirect page: " + ioE.getMessage(), ioE);
        }
    }
}
