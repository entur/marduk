package no.rutebanken.marduk.repository;

import com.google.cloud.Page;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobListOption;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Date;
import java.util.Iterator;

@Repository
@Profile("test")
public class GcsBlobStoreRepository implements BlobStoreRepository {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${blobstore.container.name}")
    private String containerName;

    @Autowired
    private Storage storage;

    @Override
    public BlobStoreFiles listBlobs(String prefix) {
        logger.debug("Listing blobs in bucket " + containerName + " with prefix " + prefix + " recursively.");
        Page<Blob> blobs = storage.list(containerName, BlobListOption.prefix(prefix));
        Iterator<Blob> blobIterator = blobs.iterateAll();
        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();
        while (blobIterator.hasNext()) {
            Blob blob = blobIterator.next();
            blobStoreFiles.add(new BlobStoreFiles.File(blob.name(), new Date(blob.updateTime()), blob.size()));
        }
        return blobStoreFiles;
    }

    @Override
    public InputStream getBlob(String name) {
        logger.debug("Fetching blob " + name + " from bucket " + containerName);
        BlobId blobId = BlobId.of(containerName, name);
        Blob blob = storage.get(blobId);
        InputStream result = null;
        if (blob != null) {
            try (ReadChannel reader = blob.reader()) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                WritableByteChannel channel = Channels.newChannel(outputStream);
                ByteBuffer bytes = ByteBuffer.allocate(64 * 1024);
                while (reader.read(bytes) > 0) {
                    bytes.flip();
                    channel.write(bytes);
                    bytes.clear();
                }
                result = new ByteArrayInputStream(outputStream.toByteArray());
                logger.info("Retrieved blob with name '" + blob.name() + "' and size '" + blob.size() + "' from bucket '" + blob.bucket() + "'");
            } catch (IOException e){
                throw new RuntimeException(e);
            }
        } else {
            logger.warn("Could not find '" + blobId.name() + "' in bucket '" + blobId.bucket() + "'");
        }
        return result;
    }

    @Override
    public void uploadBlob(String name, InputStream inputStream) {
        logger.debug("Uploading blob " + name + " to bucket " + containerName);
        BlobId blobId = BlobId.of(containerName, name);
        BlobInfo blobInfo = BlobInfo.builder(blobId).contentType("application/octet-stream").build();
        Blob blob = storage.create(blobInfo, inputStream);
        logger.info("Stored blob with name '" + blob.name() + "' and size '" + blob.size() + "' in bucket '" + blob.bucket() + "'");
    }

}
