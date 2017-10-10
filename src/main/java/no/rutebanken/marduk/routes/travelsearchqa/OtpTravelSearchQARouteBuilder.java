package no.rutebanken.marduk.routes.travelsearchqa;

import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.geocoder.routes.pelias.babylon.StartFile;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
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
    @Value("${babylon.url:http4://babylon/babylon/api}")
    private String babylonUrl;

        @Value("${otp.travelsearch.qa.job:otp-travelsearch-qa-pod.yaml}")
    private String otpTravelSearchJobFilename;

    @Value("${otp.travelsearch.qa.cron.schedule:0+15+10+?+*+*}")
    private String cronSchedule;

    @Override
    public void configure() throws Exception {
        super.configure();

        onException(MardukException.class)
                .log(LoggingLevel.ERROR, "Failed while initiating " + otpTravelSearchJobFilename)
                .handled(true);


        from("direct:runOtpTravelSearchQA")
                .log(LoggingLevel.INFO, "Requesting Babylon to start "+ otpTravelSearchJobFilename)
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .setBody(constant(new StartFile(otpTravelSearchJobFilename)))
                .marshal().json(JsonLibrary.Jackson)
                .to(babylonUrl + "/run")
                .log(LoggingLevel.INFO, "Invocation of otp travel search complete")
                .routeId("otp-travelsearch-qa-by-called");


        singletonFrom("quartz2://marduk/triggerOtpTravelSearchQA?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .filter(e -> isLeader(e.getFromRouteId()))
                .log(LoggingLevel.INFO, "Quartz triggers otp travel search.")
                .to("direct:runOtpTravelSearchQA")
                .log(LoggingLevel.INFO, "Quartz processing otp travel search done.")
                .routeId("otp-travelsearch-qa-by-trigger");
    }

}
