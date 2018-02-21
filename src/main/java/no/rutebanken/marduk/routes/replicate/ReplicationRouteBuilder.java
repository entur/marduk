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

package no.rutebanken.marduk.routes.replicate;

import no.rutebanken.marduk.exceptions.MardukException;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.backup.StartFile;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Run the job which restores databases / marduk data for the desired environment.
 * The job configuration resides in babylon
 */
@Component
public class ReplicationRouteBuilder extends BaseRouteBuilder {
    @Value("${babylon.url:http4://babylon/services/local}")
    private String babylonUrl;

    @Value("${replication.job:replication-job.yaml}")
    private String replicationJobFilename;

    // Note that the expression is designed to never run (as default)
    // In test and radon, the following is a suggestion: 0+7+5+?+*+*
    @Value("${replication.job.cron.schedule:0+0+0+1+1+?+2099}")
    private String cronSchedule;

    @Override
    public void configure() throws Exception {
        super.configure();

        onException(MardukException.class)
                .log(LoggingLevel.ERROR, "Failed while restoring data.")
                .handled(true);


        from("direct:runReplicationJob")
                .log(LoggingLevel.INFO, "Requesting Babylon to start replication job")
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .setBody(constant(new StartFile(replicationJobFilename)))
                .marshal().json(JsonLibrary.Jackson)
                .to(babylonUrl + "/job/run")
                .log(LoggingLevel.INFO, "Invocation of replication job complete")
                .routeId("replication-job-called");


        singletonFrom("quartz2://marduk/triggerReplication?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .filter(e -> shouldQuartzRouteTrigger(e, cronSchedule))
                .log(LoggingLevel.INFO, "Quartz triggers replication job.")
                .to("direct:runReplicationJob")
                .log(LoggingLevel.INFO, "Quartz processing done.")
                .routeId("replication-job-trigger");
    }

}
