package no.rutebanken.marduk.routes;

import no.rutebanken.marduk.beans.Md5Bean;
import no.rutebanken.marduk.beans.Md5ExtractorBean;
import org.apache.camel.component.jms.JmsMessageType;
import org.apache.camel.model.RouteDefinition;
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

        from("timer://start?period=30000s")
                .log(">>> Starting map data download.")
                    .to("http4://download.geofabrik.de/europe/norway-latest.osm.pbf")
                    .to("file:target/files/input/mapdata?fileName=$simple{date:now:yyyyMMddHHmmss}.pbf")
                    .log("Downloaded .pbf file.")
                    .to("http4://download.geofabrik.de/europe/norway-latest.osm.pbf.md5")
                    .to("file:target/files/input/mapdata?fileName=$simple{date:now:yyyyMMddHHmmss}.md5")
                    .log("Downloaded .md5 file.");


        from("file:target/files/input/mapdata?readLock=changed&readLockCheckInterval=5000&readLockTimeout=30000&preMove=processed&noop=true")
            .log("Got new file with name: ${header.CamelFileNameOnly}")
                .choice()
                .when(header("CamelFileNameOnly").endsWith(".md5"))
                    .setHeader("md5", method(Md5ExtractorBean.class))
                    .log("Set md5 header from .md5 file to: ${header.md5}")
                .otherwise()
                    .setHeader("md5", method(Md5Bean.class))
                    .log("Set md5 header generated from file to: ${header.md5}")
                .end()
                .aggregate(header("md5"), new Md5AggregatorStrategy())
                    .completionSize(2)
                    .completionTimeout(100000)
                    .discardOnCompletionTimeout()
                    .log("Moving map data file to output folder.")
                    .to("file:target/files/output/mapdata?exclude=*.md5")
                    .log("Done validating map data.");


    }
}
