package no.rutebanken.marduk.routes;

import no.rutebanken.marduk.beans.Md5ExtractorBean;
import org.apache.camel.spring.boot.FatJarRouter;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A spring-boot application that includes a Camel route builder to setup the Camel routes
 */
@SpringBootApplication
public class MapDataRoute extends FatJarRouter {

    // must have a main method spring-boot can run
    public static void main(String[] args) {
        FatJarRouter.main(args);
    }

    @Override
    public void configure() throws Exception {


        //TODO change order of md5 and pbf file
        from("quartz2://backend/mapdata?cron=0+0/5+*+?+*+MON-FRI")
                .log("Starting map data download.")

                    .to("http4://download.geofabrik.de/europe/norway-latest.osm.pbf.md5")
                    .to("file:target/files/input/mapdata?fileName=$simple{date:now:yyyyMMddHHmmss}.md5")
                    .log("Downloaded .md5 file.")

                .to("http4://download.geofabrik.de/europe/norway-latest.osm.pbf")
                .to("file:target/files/input/mapdata?fileName=$simple{date:now:yyyyMMddHHmmss}.pbf")
                .log("Downloaded .pbf file.");


        from("file:target/files/input/mapdata?readLock=changed")
            .log("Got new file with name: ${header.CamelFileNameOnly}")
                .setHeader("md5").method(Md5ExtractorBean.class)
                    .log("Set md5 header from ${header.CamelFileNameOnly} file to: ${header.md5}")
                    .aggregate(header("md5"), new Md5AggregationStrategy())
                    .completionSize(2)
                    .completionTimeout(100000)
                    .discardOnCompletionTimeout()
                    .log("Moving map data file with name: ${header.CamelFileNameOnly} to output folder.")
                    .to("file:target/files/output/mapdata?exclude=.md5")
                    .log("Done validating map data file with name: ${header.CamelFileNameOnly}.");

        from("file:target/files/output/mapdata?readLock=change")
                .log("Received validated map data file with name: ${header.CamelFileNameOnly}. Adding to queue.")
                .to("activemq:queue:MapDataQueue")
                .log("Done pushing map data file with name: ${header.CamelFileNameOnly} to queue.");

        from("activemq:queue:MapDataQueue")
                .log("Received JMS message on test client.")
                .to("file:target/files/testclient")
                .log("JMS message stored on test client.");

    }
}
