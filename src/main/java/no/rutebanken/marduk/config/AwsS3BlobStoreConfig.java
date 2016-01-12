package no.rutebanken.marduk.config;

import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStoreContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class AwsS3BlobStoreConfig extends CommonBlobStoreConfig {

    @Value("${blobstore.aws-s3.identity}")
    private String identity;

    @Value("${blobstore.aws-s3.credential}")
    private String credential;

    @Bean
    public BlobStoreContext blobStoreContext() {
         BlobStoreContext context = ContextBuilder.newBuilder(provider)
                .credentials(identity, credential)
                .endpoint("https://s3-eu-west-1.amazonaws.com")
                .buildView(BlobStoreContext.class);
        return context;
    }

}
