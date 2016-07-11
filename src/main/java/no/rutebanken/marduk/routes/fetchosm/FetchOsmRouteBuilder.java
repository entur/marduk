package no.rutebanken.marduk.routes.fetchosm;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.exceptions.Md5ChecksumValidationException;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;

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

    private static final String NEED_TO_REFETCH = "needToRefetchMapFile";

    /** One time per 24H on MON-FRI */
    @Value("${fetch.osm.cron.schedule:0+*+*/23+?+*+MON-FRI}")
    private String cronSchedule;

    //@Value("${fetch.osm.map.url:http4://jump.rutebanken.org/testfile.txt}")
    @Value("${fetch.osm.map.url:http4://download.geofabrik.de/europe/norway-latest.osm.pbf}")
    private String osmMapUrl;

    /**
     * Into which subdirectory should the map be stored. The default is osm.
     */
    @Value("${osm.pbf.blobstore.subdirectory:osm}")
    private String blobStoreSubdirectory;


    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:fetchOsmMapOverNorway")
        //from("activemq:queue:FetchOsmMapOverNorway?maxConcurrentConsumers=1")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching OSM map over Norway.")
                .to("direct:fetchOsmMapOverNorwayMd5")
                // Storing the MD5
                .convertBodyTo(InputStream.class)
                .setHeader(FILE_HANDLE, simple(blobStoreSubdirectory +"/"+"norway-latest.osm.pbf.md5"))
                .to("direct:uploadBlob")
                // Fetch the actual file
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .to( osmMapUrl )
                .convertBodyTo(String.class)
                .process(p -> {
                    // Throw exception if the expected MD5 does not match MD5 from body
                    String body = (String) p.getIn().getBody();
                    String md5 = DigestUtils.md5Hex( body );
                    String md5FromFile = (String) p.getIn().getHeader(Constants.FILE_TARGET_MD5);
                    if (! md5.equals( md5FromFile)) {
                        throw new Md5ChecksumValidationException("MD5 of body ("+md5+") does not match MD5 which was read from source ("+md5FromFile+")");
                    }
                })
                // Probably not needed: .convertBodyTo(InputStream.class)
                .setHeader(FILE_HANDLE, simple(blobStoreSubdirectory +"/"+"norway-latest.osm.pbf"))
                .to("direct:uploadBlob")
                .setBody(simple("File fetched, and blob store has been correctly updated"))
                .log(LoggingLevel.DEBUG, getClass().getName(), "Processing of OSM map finished");

        from("direct:fetchOsmMapOverNorwayMd5")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching MD5 sum for map over Norway")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .to( osmMapUrl+".md5" )
                .convertBodyTo(String.class)
                .process(p -> {
                    String body = (String) p.getIn().getBody();
                    String md5 = body.split(" ")[0];
                    p.getOut().setHeader(Constants.FILE_TARGET_MD5, md5);
                    p.getOut().setBody( md5 );
                })
                .log(LoggingLevel.DEBUG, getClass().getName(), "MD5 sum fetched and set in header");

        from("direct:considerToFetchOsmMapOverNorway")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Route which figures out whether to fetch OSM map or not")
                .to("direct:fetchOsmMapOverNorwayMd5")
                .setHeader(FILE_HANDLE, simple(blobStoreSubdirectory +"/"+"norway-latest.osm.pbf.md5"))
                .to("direct:getBlob")
                .convertBodyTo(String.class)
                .process(p -> {
                    String md5 = (String) p.getIn().getBody();
                    if ( md5 == null || md5.length() == 0) {
                        md5 ="just to flag that we need to fetch the data, as the file did not exist";
                    }
                    String md5FromHead = (String) p.getIn().getHeader(Constants.FILE_TARGET_MD5);
                    p.getIn().setHeader(NEED_TO_REFETCH, ""+( ! md5.equals( md5FromHead  )) );
                })
                .choice()
                .when(header(NEED_TO_REFETCH).isEqualTo("false"))
                    .log(LoggingLevel.INFO, getClass().getName(), "There is no update of the map file. No need to fetch external file")
                    .setBody(simple("No need to updated the map file, as the MD5 sum has not changed"))
                .otherwise()
                    .log(LoggingLevel.INFO, getClass().getName(), "Need to update the map file. Calling the update map route")
                    .inOnly("direct:fetchOsmMapOverNorway")
                    .setBody(simple("Need to fetch map file. Called update map route"))
                    //.to( "activemq:queue:FetchOsmMapOverNorway")
                .end();

        from("quartz2://marduk/fetchOsmMap?cron="+cronSchedule+"&trigger.timeZone=Europe/Oslo")
                .log(LoggingLevel.INFO, getClass().getName(), "Quartz triggers fetch of OSM map over Norway.")
                .to("direct:considerToFetchOsmMapOverNorway")
                .log(LoggingLevel.INFO, getClass().getName(), "Quartz processing done.");
    }
}
