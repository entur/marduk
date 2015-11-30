package no.rutebanken.marduk;

import no.rutebanken.marduk.config.FileSystemBlobStoreConfig;
import org.apache.camel.component.jclouds.JcloudsComponent;
import org.apache.camel.spring.boot.FatJarRouter;
import org.jclouds.blobstore.BlobStore;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.Collections;

/**
 * A spring-boot application that includes a Camel route builder to setup the Camel routes
 */
@SpringBootApplication
@Import(FileSystemBlobStoreConfig.class)
public class App extends FatJarRouter {

    // must have a main method spring-boot can run
    public static void main(String[] args) {
        FatJarRouter.main(args);
    }

    @Bean
    public JcloudsComponent jclouds(BlobStore blobStore) {
        JcloudsComponent jcloudsComponent = new JcloudsComponent();
        jcloudsComponent.setBlobStores(Collections.singletonList(blobStore));
        return jcloudsComponent;
    }

}
