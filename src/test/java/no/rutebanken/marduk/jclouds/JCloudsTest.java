package no.rutebanken.marduk.jclouds;

import org.apache.commons.io.IOUtils;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.junit.Ignore;
import org.junit.Test;

import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;

import static org.junit.Assert.fail;

/**
 * Test for exploring AWS S3 setup.
 */
public class JCloudsTest {

    private static String blobName = "test_" + LocalDateTime.now() + ".zip";

    String accessKeyId = "";
    String secretKey = "";
    String bucket = "junit-test-rutebanken";

    @Ignore
    @Test
    public void upload() throws Exception {
        File file = new File("/home/dev/code/000111-gtfs.zip");
        Blob blob = getBlobStore().blobBuilder(blobName).payload(file).contentType(MediaType.APPLICATION_OCTET_STREAM).contentDisposition(blobName).build();
        getBlobStore().putBlob(bucket, blob);
        System.out.println("Stored '" + blobName + "'");
    }

    private BlobStore getBlobStore() {
        // get a context with amazon that offers the portable BlobStore API
        BlobStoreContext context = ContextBuilder.newBuilder("aws-s3")
                .credentials(accessKeyId, secretKey)
                .endpoint("https://s3-eu-west-1.amazonaws.com")
                .buildView(BlobStoreContext.class);

        // create a container in the default location
        BlobStore blobStore = context.getBlobStore();
        blobStore.createContainerInLocation(null, bucket);
        return blobStore;
    }

    @Ignore
    @Test
    public void download() throws Exception {
        System.out.println("Fetching for: '" + blobName);
        BlobStore blobStore = getBlobStore();
        Blob blob = blobStore.getBlob(bucket, blobName);
        if (blob == null){
            fail("No blob found for " + blobName);
        }
        OutputStream stream = new FileOutputStream(new File("/home/dev/code/tmp/result.zip"));
        IOUtils.copy(blob.getPayload().openStream(), stream);
    }


}
