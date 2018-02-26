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

package no.rutebanken.marduk.routes.google;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;

/**
 * Route publishing GTFS exports to google.
 *
 * Supports regular production export in addition to a separate dataset for testing / QA / onboarding new providers.
 */
@Component
public class GoogleGtfsPublishRoute extends BaseRouteBuilder {

    /**
     * Every morning at 4 AM.
     */
    @Value("${google.publish.cron.schedule:0+0+4+?+*+*}")
    private String cronSchedule;


    @Value("${google.export.file.name:google/google_norway-aggregated-gtfs.zip}")
    private String googleExportFileName;


    @Value("${google.publish.sftp.host:partnerupload.google.com}")
    private String googleSftpHost;


    @Value("${google.publish.sftp.port:19321}")
    private int googleSftpPort;


    @Value("${google.publish.sftp.username}")
    private String googleSftpUsername;

    @Value("${google.publish.sftp.password}")
    private String googleSftpPassword;

    // Config for QA / test dataset
    /**
     * Every morning at 4:30 AM.
     */
    @Value("${google.publish.qa.cron.schedule:0+30+4+?+*+*}")
    private String qaCronSchedule;


    @Value("${google.export.qa.file.name:google/google_norway-aggregated-qa-gtfs.zip}")
    private String googleQaExportFileName;


    @Value("${google.publish.qa.sftp.username:NA}")
    private String googleQaSftpUsername;

    @Value("${google.publish.qa.sftp.password:NA}")
    private String googleQAaSftpPassword;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz2://marduk/googleExportPublish?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{google.publish.scheduler.autoStartup:true}}")
                .filter(e -> shouldQuartzRouteTrigger(e, cronSchedule))
                .log(LoggingLevel.INFO, "Quartz triggers publish of google gtfs export.")
                .inOnly("activemq:queue:GooglePublishQueue")
                .routeId("google-publish--quartz");


        singletonFrom("quartz2://marduk/googleQaExportPublish?cron=" + qaCronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{google.publish.qa.scheduler.autoStartup:true}}")
                .filter(e -> shouldQuartzRouteTrigger(e, qaCronSchedule))
                .log(LoggingLevel.INFO, "Quartz triggers publish of google gtfs QA export.")
                .inOnly("activemq:queue:GooglePublishQaQueue")
                .routeId("google-publish-qa-quartz");


        singletonFrom("activemq:queue:GooglePublishQueue?transacted=true&maxConcurrentConsumers=1&messageListenerContainerFactoryRef=batchListenerContainerFactory").autoStartup("{{google.publish.autoStartup:true}}")
                .transacted()

                .log(LoggingLevel.INFO, getClass().getName(), "Start publish of GTFS file to Google")

                .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + "gtfs/" + googleExportFileName))
                .to("direct:getBlob")
                .setHeader(Exchange.FILE_NAME, constant(googleExportFileName))
                .to("sftp:" + googleSftpUsername + ":" + googleSftpPassword + "@" + googleSftpHost + ":" + googleSftpPort)

                .log(LoggingLevel.INFO, getClass().getName(), "Completed publish of GTFS file to Google")
                .routeId("google-publish-route");


        singletonFrom("activemq:queue:GooglePublishQaQueue?transacted=true&maxConcurrentConsumers=1&messageListenerContainerFactoryRef=batchListenerContainerFactory").autoStartup("{{google.publish.qa.autoStartup:false}}")
                .transacted()

                .log(LoggingLevel.INFO, getClass().getName(), "Start publish of GTFS QA file to Google")

                .setHeader(FILE_HANDLE, simple(BLOBSTORE_PATH_OUTBOUND + "gtfs/" + googleQaExportFileName))
                .to("direct:getBlob")
                .setHeader(Exchange.FILE_NAME, constant(googleQaExportFileName))
                .to("sftp:" + googleQaSftpUsername + ":" + googleQAaSftpPassword + "@" + googleSftpHost + ":" + googleSftpPort)

                .log(LoggingLevel.INFO, getClass().getName(), "Completed publish of GTFS QA file to Google")
                .routeId("google-publish-qa-route");


    }
}
