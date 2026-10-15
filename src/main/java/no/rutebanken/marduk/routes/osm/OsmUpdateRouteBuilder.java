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

import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.regex.Pattern;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
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

    private static final Pattern MD5_PATTERN = Pattern.compile("[0-9a-f]{32}");

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

        String md5FileHandle = blobStoreSubdirectoryForOsm + "/" + OSM_MD5_FILE_NAME;

        from("direct:fetchOsmMapMd5")
                .log(LoggingLevel.DEBUG, correlation() + "Fetching MD5 sum for map over Norway")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http.HttpMethods.GET))
                .to(osmMapUrl + ".md5")
                .convertBodyTo(String.class)
                .process(p -> {
                    String md5FileContent = p.getIn().getBody(String.class);
                    validateMd5FileContent(md5FileContent);
                    p.setProperty(OSM_MD5_FILE_CONTENT, md5FileContent);
                })
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "MD5 sum fetched")
                .routeId("osm-fetch-md5sum");

        from("direct:checkForOsmMapUpdate")
                .log(LoggingLevel.DEBUG, correlation() + "Route which figures out whether the OSM map has been updated or not")
                .to("direct:fetchOsmMapMd5")
                .setHeader(FILE_HANDLE, constant(md5FileHandle))
                .to("direct:getBlob")
                .convertBodyTo(String.class)
                .process(p -> {
                    String storedMd5FileContent = p.getIn().getBody(String.class);
                    String currentMd5FileContent = p.getProperty(OSM_MD5_FILE_CONTENT, String.class);
                    boolean updated = storedMd5FileContent == null
                            || storedMd5FileContent.isEmpty()
                            || !extractMd5(storedMd5FileContent).equals(extractMd5(currentMd5FileContent));
                    p.setProperty(OSM_MAP_UPDATED, updated);
                })
                .choice()
                .when(exchangeProperty(OSM_MAP_UPDATED).isEqualTo(false))
                .log(LoggingLevel.INFO, correlation() + "There is no update of the map file. No need to build the base graph")
                .setBody(simple("No need to build the base graph, as the MD5 sum of the map file has not changed"))
                .otherwise()
                .log(LoggingLevel.INFO, correlation() + "The map file was updated, therefore triggering OSM base graph build")
                // Remove the HTTP response headers and the route-specific headers so that they do not end up as PubSub attributes
                .removeHeaders("*", CORRELATION_ID)
                .setBody(constant(""))
                .to(ExchangePattern.InOnly, "google-pubsub:{{marduk.pubsub.project.id}}:Otp2BaseGraphBuildQueue")
                // Store the checksum only after the build has been triggered, so that a failed trigger is retried on the next run
                .setHeader(FILE_HANDLE, constant(md5FileHandle))
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
     * Fail if the content fetched from the osm-data bucket is not a valid MD5 file, so that an empty or
     * unexpected response (truncated object, HTML error page) is neither compared with nor stored as a checksum.
     */
    private void validateMd5FileContent(String md5FileContent) {
        if (md5FileContent == null || !MD5_PATTERN.matcher(extractMd5(md5FileContent)).matches()) {
            throw new MardukException("Invalid MD5 file content fetched from " + osmMapUrl + ".md5: '" + md5FileContent + "'");
        }
    }

    /**
     * Extract the checksum from the content of an MD5 file, formatted as "checksum filename".
     */
    private static String extractMd5(String md5FileContent) {
        return md5FileContent.split(" ")[0];
    }

}
