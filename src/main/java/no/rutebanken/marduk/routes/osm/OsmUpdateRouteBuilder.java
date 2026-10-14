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

package no.rutebanken.marduk.routes.osm;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;

/**
 * Detect updates of the OSM data file and trigger a base graph build when the file has changed.
 * <p>
 * The OSM data file is maintained in the shared osm-data bucket (see the entur/osm-data project), together with
 * an MD5 checksum file obtained by adding <code>.md5</code> to the file URL. The graph builder reads the OSM
 * data file directly from that bucket, so Marduk does not download it. Marduk only keeps a copy of the checksum
 * of the last OSM data file for which a base graph build was triggered, and compares it with the current checksum.
 * </p>
 */
@Component
public class OsmUpdateRouteBuilder extends BaseRouteBuilder {

    private static final String OSM_MAP_UPDATED = "osmMapUpdated";

    private static final String OSM_MD5_FILE_CONTENT = "osmMd5FileContent";

    private static final String OSM_MD5_FILE_NAME = "norway-latest.osm.pbf.md5";

    /**
     * One time per 24H on MON-FRI
     */
    @Value("${fetch.osm.cron.schedule:0+*+*/23+?+*+MON-FRI}")
    private String cronSchedule;

    @Value("${fetch.osm.map.url:https://storage.googleapis.com/ror-osmdata-prd/osm-data/norway-latest.osm.pbf}")
    private String osmMapUrl;

    /**
     * Into which subdirectory should the checksum of the last built map be stored. The default is osm.
     */
    @Value("${osm.pbf.blobstore.subdirectory:osm}")
    private String blobStoreSubdirectoryForOsm;

    @Override
    public void configure() throws Exception {
        super.configure();

        onException(MardukException.class)
                .log(LoggingLevel.ERROR, correlation() + "Failed while checking for OSM file update (${exception.stacktrace}).")
                .handled(true);

        from("direct:fetchOsmMapMd5")
                .log(LoggingLevel.DEBUG, correlation() + "Fetching MD5 sum for map over Norway")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http.HttpMethods.GET))
                .to(osmMapUrl + ".md5")
                .convertBodyTo(String.class)
                .process(p -> {
                    String body = (String) p.getIn().getBody();
                    p.getMessage().setHeader(Constants.FILE_TARGET_MD5, extractMd5(body));
                    p.setProperty(OSM_MD5_FILE_CONTENT, body);
                })
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "MD5 sum fetched and set in header")
                .routeId("osm-fetch-md5sum");

        from("direct:checkForOsmMapUpdate")
                .log(LoggingLevel.DEBUG, correlation() + "Route which figures out whether the OSM map has been updated or not")
                .to("direct:fetchOsmMapMd5")
                .setHeader(FILE_HANDLE, simple(blobStoreSubdirectoryForOsm + "/" + OSM_MD5_FILE_NAME))
                .to("direct:getBlob")
                .convertBodyTo(String.class)
                .process(p -> {
                    String storedMd5 = (String) p.getIn().getBody();
                    if (storedMd5 == null || storedMd5.isEmpty()) {
                        storedMd5 = "flag that the map was never built, as the checksum file did not exist";
                    }
                    storedMd5 = extractMd5(storedMd5);
                    String currentMd5 = (String) p.getIn().getHeader(Constants.FILE_TARGET_MD5);
                    p.getIn().setHeader(OSM_MAP_UPDATED, !storedMd5.equals(currentMd5));
                })
                .choice()
                .when(header(OSM_MAP_UPDATED).isEqualTo(false))
                .log(LoggingLevel.INFO, correlation() + "There is no update of the map file. No need to build the base graph")
                .setBody(simple("No need to build the base graph, as the MD5 sum of the map file has not changed"))
                .otherwise()
                .log(LoggingLevel.INFO, correlation() + "The map file was updated, therefore triggering OSM base graph build")
                .setBody(constant(""))
                .to(ExchangePattern.InOnly, "google-pubsub:{{marduk.pubsub.project.id}}:Otp2BaseGraphBuildQueue")
                // Store the checksum only after the build has been triggered, so that a failed trigger is retried on the next run
                .setBody(exchangeProperty(OSM_MD5_FILE_CONTENT))
                .convertBodyTo(InputStream.class)
                .to("direct:uploadBlob")
                .setBody(simple("The map file was updated. Triggered the base graph build"))
                .end()
                .routeId("osm-check-for-newer-map");

        singletonFrom("quartz://marduk/fetchOsmMap?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .filter(e -> shouldQuartzRouteTrigger(e, cronSchedule))
                .process(this::setNewCorrelationId)
                .log(LoggingLevel.INFO, correlation() + "Quartz triggers check for OSM map update.")
                .to("direct:checkForOsmMapUpdate")
                .log(LoggingLevel.INFO, correlation() + "Quartz processing done for checking OSM map update.")
                .routeId("osm-trigger-update-check");
    }

    /**
     * Extract the checksum from the content of an MD5 file, formatted as "checksum filename".
     */
    private static String extractMd5(String md5FileContent) {
        return md5FileContent.split(" ")[0];
    }

}
