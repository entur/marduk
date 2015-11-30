package no.rutebanken.marduk.routes.gtfs;

import no.rutebanken.marduk.routes.BaseRoute;
import org.springframework.stereotype.Component;

/**
 * Performs deeper validation of GTFS files
 */
@Component
public class GtfsRoute extends BaseRoute {

    @Override
    public void configure() throws Exception {
        super.configure();

        from("activemq:queue:ProcessGtfsQueue")
            .to("log:" + getClass().getSimpleName() + "?showAll=true&multiline=true");

    }

}
