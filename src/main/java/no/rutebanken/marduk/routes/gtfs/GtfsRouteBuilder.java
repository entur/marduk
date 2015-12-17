package no.rutebanken.marduk.routes.gtfs;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Performs deeper validation of GTFS files and distribute further.
 */
@Component
public class GtfsRouteBuilder extends BaseRouteBuilder {

    @Override
    public void configure() throws Exception {
        super.configure();

        from("activemq:queue:ProcessGtfsQueue")
            .pipeline("direct:validate1", "direct:sendToChouette");

            from("direct:validate1")
                .log("Validation 1 will be done at this point.");

            from("direct:sendToChouette")
                .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .to("activemq:queue:ChouetteImportQueue");

    }

}
