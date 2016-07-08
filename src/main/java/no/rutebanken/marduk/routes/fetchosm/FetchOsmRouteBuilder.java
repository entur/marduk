package no.rutebanken.marduk.routes.fetchosm;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;

/**
 * Fetch data file as listed on: http://download.geofabrik.de/europe/norway.html
 * This is expected to be http://download.geofabrik.de/europe/norway-latest.osm.pbf
 *
 * <p>
 *     The MD5 sum is found by adding <code>.md5</code> to the URL
 * </p>
 * <p>
 *     <ul>
 *         <li>download pbf</li>
 *         <li>give to s3 upload queue</li>
 *     </ul>
 * </p>
 */
@Component
public class FetchOsmRouteBuilder extends BaseRouteBuilder {
    @Value("${fetch.osm.cron.schedule:0+*+*/23+?+*+MON-FRI}")
    private String cronSchedule;

    //@Value("${fetch.osm.map.url:http4://download.geofabrik.de/europe/norway-latest.osm.pbf}")
    @Value("${fetch.osm.map.url:http4://jump.rutebanken.org/testfile.txt}")
    private String osmMapUrl;


    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:fetchOsmMapOverNorway")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching OSM map over Norway.")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .to( osmMapUrl )
                .setHeader(FILE_HANDLE, simple("osm/norway-latest.osm.pbf"))
                //.to("direct:uploadBlob")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Processing of OSM map finished");


        // One time per 24H on MON-FRI
        from("quartz2://marduk/fetchOsmMap?cron="+cronSchedule+"&trigger.timeZone=Europe/Oslo")
                .log(LoggingLevel.INFO, getClass().getName(), "Quartz triggers fetch of OSM map over Norway.")
                .to("direct:fetchOsmMapOverNorway")
                .log(LoggingLevel.INFO, getClass().getName(), "Quartz processing done.");
    }
}
