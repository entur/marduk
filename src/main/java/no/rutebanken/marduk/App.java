package no.rutebanken.marduk;

import no.rutebanken.marduk.config.AwsS3BlobStoreConfig;
import no.rutebanken.marduk.config.FileSystemBlobStoreConfig;
import org.apache.camel.spring.boot.FatJarRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * A spring-boot application that includes a Camel route builder to setup the Camel routes
 */
@SpringBootApplication
@Import({FileSystemBlobStoreConfig.class, AwsS3BlobStoreConfig.class})
public class App extends FatJarRouter {

    private static Logger logger = LoggerFactory.getLogger(App.class);

    // must have a main method spring-boot can run
    public static void main(String[] args) {
        logger.info("Starting Marduk...");
        FatJarRouter.main(args);
    }

}
