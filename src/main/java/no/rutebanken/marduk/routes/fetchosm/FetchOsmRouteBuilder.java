package no.rutebanken.marduk.routes.fetchosm;

import no.rutebanken.marduk.exceptions.Md5ChecksumValidationException;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
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

    private static final String FILE_TARGET_MD5 = "targetMd5Sum";

    @Value("${fetch.osm.cron.schedule:0+*+*/23+?+*+MON-FRI}")
    private String cronSchedule;

    //@Value("${fetch.osm.map.url:http4://download.geofabrik.de/europe/norway-latest.osm.pbf}")
    @Value("${fetch.osm.map.url:http4://jump.rutebanken.org/testfile.txt}")
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
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching OSM map over Norway.")
                .to("direct:fetchOsmMapOverNorwayMd5")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .to( osmMapUrl )
                .convertBodyTo(String.class)
                .process(p -> {
                    // Throw exception if the expected MD5 does not match MD5 from body
                    String body = (String) p.getIn().getBody();
                    String md5 = DigestUtils.md5Hex( body );
                    String md5FromFile = (String) p.getIn().getHeader(FILE_TARGET_MD5);
                    if (! md5.equals( md5FromFile)) {
                        throw new Md5ChecksumValidationException("MD5 of body ("+md5+") does not match MD5 which was read from source ("+md5FromFile+")");
                    }
                })
                .convertBodyTo(InputStream.class)
                .setHeader(FILE_HANDLE, simple(blobStoreSubdirectory +"/"+"osm/norway-latest.osm.pbf"))
                .to("direct:uploadBlob")
                .setBody(simple("File fetched, and blob store has been correctly updated"))
                .log(LoggingLevel.DEBUG, getClass().getName(), "Processing of OSM map finished");

        from("direct:fetchOsmMapOverNorwayMd5")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching MD5 sum for map over")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .to( osmMapUrl+".md5" )
                .convertBodyTo(String.class)
                .process(p -> {
                    String body = (String) p.getIn().getBody();
                    String md5 = body.split(" ")[0];
                    p.getOut().setHeader(FILE_TARGET_MD5, md5);
                })
                .log(LoggingLevel.DEBUG, getClass().getName(), "MD5 sum fetched and set in header");

        // One time per 24H on MON-FRI
        from("quartz2://marduk/fetchOsmMap?cron="+cronSchedule+"&trigger.timeZone=Europe/Oslo")
                .log(LoggingLevel.INFO, getClass().getName(), "Quartz triggers fetch of OSM map over Norway.")
                .to("direct:fetchOsmMapOverNorway")
                .log(LoggingLevel.INFO, getClass().getName(), "Quartz processing done.");
    }
}
