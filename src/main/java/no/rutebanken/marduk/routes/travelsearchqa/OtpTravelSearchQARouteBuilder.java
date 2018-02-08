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

package no.rutebanken.marduk.routes.travelsearchqa;

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
 * Triggers otp travelsearch qa job.
 * It starts a (self-contained) pod which runs the tests.
 */
@Component
public class OtpTravelSearchQARouteBuilder extends BaseRouteBuilder {

    @Value("${babylon.url:http4://babylon/services/local}")
    private String babylonUrl;

    @Value("${otp.travelsearch.qa.job:otp-travelsearch-qa-pod.yaml}")
    private String otpTravelSearchJobFilename;

    @Value("${otp.travelsearch.qa.cron.schedule:0+0/20+*+?+*+*}")
    private String cronSchedule;

    @Override
    public void configure() throws Exception {
        super.configure();

        onException(MardukException.class)
                .log(LoggingLevel.ERROR, "Failed while initiating " + otpTravelSearchJobFilename)
                .handled(true);


        from("direct:runOtpTravelSearchQA")
                .log(LoggingLevel.INFO, "Requesting Babylon to start " + otpTravelSearchJobFilename)
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .setBody(constant(new StartFile(otpTravelSearchJobFilename)))
                .marshal().json(JsonLibrary.Jackson)
                .to(babylonUrl + "/job/run")
                .log(LoggingLevel.INFO, "Invocation of otp travel search complete")
                .routeId("otp-travelsearch-qa-by-called");


        singletonFrom("quartz2://marduk/triggerOtpTravelSearchQA?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{otp.travelsearch.qa.autoStartup:true}}")
                .filter(e -> isSingletonRouteActive(e.getFromRouteId()))
                .log(LoggingLevel.INFO, "Quartz triggers otp travel search.")
                .to("direct:runOtpTravelSearchQA")
                .log(LoggingLevel.INFO, "Quartz processing otp travel search done.")
                .routeId("otp-travelsearch-qa-by-trigger");
    }

}
