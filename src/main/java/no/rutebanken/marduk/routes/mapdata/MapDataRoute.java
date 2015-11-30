package no.rutebanken.marduk.routes.mapdata;

import no.rutebanken.marduk.routes.mapdata.beans.Md5ChecksumExtractorBean;
import no.rutebanken.marduk.routes.mapdata.aggregation.Md5AggregationStrategy;
import org.apache.camel.builder.RouteBuilder;

/**
 * Downloads map data periodically, verifies with md5 checksum, and posts to a topic.
 */
//@Component
public class MapDataRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        from("quartz2://backend/mapdata?cron=0+0/10+*+*+*+?")
                .log("Starting map data download.")
                .to("http4://download.geofabrik.de/europe/norway-latest.osm.pbf.md5")
                .to("file:target/files/input/mapdata?fileName=$simple{date:now:yyyyMMddHHmmss}.md5")
                .log("Downloaded .md5 file.")
                .to("http4://download.geofabrik.de/europe/norway-latest.osm.pbf")
                .to("file:target/files/input/mapdata?fileName=$simple{date:now:yyyyMMddHHmmss}.pbf")
                .log("Downloaded .pbf file.");

        from("file:target/files/input/mapdata?readLock=changed")
            .log("Got new file with name: ${header.CamelFileNameOnly}")
                .setHeader("md5").method(Md5ChecksumExtractorBean.class, "extractMd5")
                    .log("Set md5 header from ${header.CamelFileNameOnly} file to: ${header.md5}")
                    .aggregate(header("md5"), new Md5AggregationStrategy())
                    .completionSize(2)
                    .completionTimeout(5 * 60 * 1000)
                    .discardOnCompletionTimeout()
                    .log("Moving map data file with name: ${header.CamelFileNameOnly} to output folder.")
                    .to("file:target/files/output/mapdata?exclude=.md5")
                    .log("Done validating map data file with name: ${header.CamelFileNameOnly}.");

        from("file:target/files/output/mapdata?readLock=change")
                .log("Received validated map data file with name: ${header.CamelFileNameOnly}. Adding to topic.")
                .to("activemq:topic:MapDataTopic")
                .log("Done pushing map data file with name: ${header.CamelFileNameOnly} to topic.");

    }

}
