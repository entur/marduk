package no.rutebanken.marduk.routes.management;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

@Component
public class TriggerRouteBuilder extends BaseRouteBuilder {

    private int chouetteGtfsExportPollDelay = 2 * 60;

    @Override
    public void configure() throws Exception {
        super.configure();
//
//        from("scheduler://chouette.export.gtfs?delay=" + chouetteGtfsExportPollDelay + "s&initialDelay=" + chouetteGtfsExportPollDelay + "s&backoffMultiplier=2&backoffIdleThreshold=1")
//                .log(LoggingLevel.INFO, getClass().getName(), "Triggering Chouette GTFS export.")
//                .log(LoggingLevel.DEBUG, getClass().getName(), "Putting trigger message on ChouetteGtfsExportTriggerQueue.")
//                .setHeader(Exchange.SCHEDULER_POLLED_MESSAGES, constant(false))     //Allow scheduler backoff by telling it a white lie
//                .to("activemq:queue:ChouetteGtfsExportTriggerQueue");


    }
}
