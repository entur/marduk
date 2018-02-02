package no.rutebanken.marduk.routes.mapbox;

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
 * Triggers mapbox update job. See https://github.com/entur/asag
 * It starts a (self-contained) pod which runs the update.
 */
@Component
public class MapboxUpdateRouteBuilder extends BaseRouteBuilder {

    @Value("${babylon.url:http4://babylon/services/local}")
    private String babylonUrl;

    @Value("${mapbox.update.job:mapbox-update-pod.yaml}")
    private String mapboxUpdateJobFilename;

    @Value("${mapbox.update.cron.schedule:0+30+6-15/3+*+*+*}")
    private String cronSchedule;

    @Override
    public void configure() throws Exception {
        super.configure();

        onException(MardukException.class)
                .log(LoggingLevel.ERROR, "Failed while initiating " + mapboxUpdateJobFilename)
                .handled(true);

        from("direct:runMapboxUpdate")
                .log(LoggingLevel.INFO, "Requesting Babylon to start " + mapboxUpdateJobFilename)
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .setBody(constant(new StartFile(mapboxUpdateJobFilename)))
                .marshal().json(JsonLibrary.Jackson)
                .to(babylonUrl + "/job/run")
                .log(LoggingLevel.INFO, "Invocation of " + mapboxUpdateJobFilename)
                .routeId("mapbox-update-by-called");

        singletonFrom("quartz2://marduk/triggerMapboxUpdate?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{mapbox.update.autoStartup:true}}")
                .filter(e -> isSingletonRouteActive(e.getFromRouteId()))
                .log(LoggingLevel.INFO, "Quartz triggers " + mapboxUpdateJobFilename)
                .to("direct:runMapboxUpdate")
                .log(LoggingLevel.INFO, "Quartz processing " + mapboxUpdateJobFilename + " done.")
                .routeId("mapbox-update-by-trigger");
    }

}
