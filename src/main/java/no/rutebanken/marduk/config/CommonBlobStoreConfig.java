package no.rutebanken.marduk.config;

import org.apache.camel.component.jclouds.JcloudsComponent;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.util.Collections;

public class CommonBlobStoreConfig {

    @Value("${blobstore.provider}")
    protected String provider;

    @Value("${blobstore.containerName}")
    protected String containerName;

    @Bean
    public BlobStore blobStore(BlobStoreContext blobStoreContext) {
        BlobStore blobStore = blobStoreContext.getBlobStore();
        blobStore.createContainerInLocation(null, containerName);
        return blobStore;
    }

    @Bean
    public JcloudsComponent jclouds(BlobStore blobStore) {
        JcloudsComponent jcloudsComponent = new JcloudsComponent();
        jcloudsComponent.setBlobStores(Collections.singletonList(blobStore));
        return jcloudsComponent;
    }

}
