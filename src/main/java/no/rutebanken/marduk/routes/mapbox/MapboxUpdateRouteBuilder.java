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

package no.rutebanken.marduk.routes.mapbox;

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
 * Triggers mapbox update job. See https://github.com/entur/asag
 * It starts a (self-contained) pod which runs the update.
 */
@Component
public class MapboxUpdateRouteBuilder extends BaseRouteBuilder {

    @Value("${babylon.url:http4://babylon/services/local}")
    private String babylonUrl;

    @Value("${mapbox.update.job:mapbox-update-pod.yaml}")
    private String mapboxUpdateJobFilename;

    @Value("${mapbox.update.cron.schedule:0+30+7-15/2+?+*+*}")
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
