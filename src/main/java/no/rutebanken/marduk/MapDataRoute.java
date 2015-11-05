package no.rutebanken.marduk;

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
        from("timer://foo?period=30000000")
                .log(">>> Starting map data download.")
                .to("http4://download.geofabrik.de/europe/norway-latest.osm.pbf")
                .to("file:target/inputfiles/mapdata")
                .log(">>> Downloaded .pbf file.")
                .to("http4://download.geofabrik.de/europe/norway-latest.osm.pbf.md5")
                .to("file:target/inputfiles/mapdata/md5")
                .log(">>> Downloaded .md5 file.");
    }
}
