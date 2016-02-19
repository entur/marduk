package no.rutebanken.marduk.config;

import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.filesystem.reference.FilesystemConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Properties;

@Configuration
@Profile({"default", "dev"})
public class FileSystemBlobStoreConfig extends CommonBlobStoreConfig {

    @Value("${blobstore.filesystem.baseDirectory}")
    private String baseDirectory;

    @Bean
    public Properties configProperties() {
        Properties properties = new Properties();
        properties.setProperty(FilesystemConstants.PROPERTY_BASEDIR, baseDirectory);
        return properties;
    }

    @Bean
    public BlobStoreContext blobStoreContext(Properties configProperties) {
        BlobStoreContext context = ContextBuilder.newBuilder(provider)
                .overrides(configProperties)
                .buildView(BlobStoreContext.class);
        return context;
    }

}
