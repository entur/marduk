package no.rutebanken.marduk.config;

import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.filesystem.reference.FilesystemConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Properties;

@Configuration
@Profile("dev")
public class FileSystemBlobStoreConfig {

    private static final String PROVIDER = "filesystem";
    private static final String CONTAINER_NAME = "test-container";  //This becomes a sub-folder

    @Bean
    public Properties fileSystemConfig() {
        Properties properties = new Properties();
        properties.setProperty(FilesystemConstants.PROPERTY_BASEDIR, "./files/filesystemstorage");
//        properties.setProperty(FilesystemConstants.PROPERTY_AUTO_DETECT_CONTENT_TYPE, "false");
        return properties;
    }

    @Bean
    public BlobStoreContext blobStoreContext(Properties fileSystemConfig) {
        BlobStoreContext context = ContextBuilder.newBuilder(PROVIDER)
                .overrides(fileSystemConfig)
                .buildView(BlobStoreContext.class);
        return context;
    }

    @Bean
    public BlobStore blobStore(BlobStoreContext blobStoreContext) {
        BlobStore blobStore = blobStoreContext.getBlobStore();
        blobStore.createContainerInLocation(null, CONTAINER_NAME);
        return blobStore;
    }


}
