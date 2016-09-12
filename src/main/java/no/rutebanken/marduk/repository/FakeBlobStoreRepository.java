package no.rutebanken.marduk.repository;

import no.rutebanken.marduk.domain.BlobStoreFiles;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@Profile("dev")
public class FakeBlobStoreRepository implements BlobStoreRepository {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private Map<String, byte[]> uploads = new HashMap<>();

    @Override
    public BlobStoreFiles listBlobs(String prefix) {
        logger.debug("list blobs called in fake");
        List<BlobStoreFiles.File> files = uploads.keySet().stream()
                .filter(k -> !k.startsWith(prefix + "/"))
                .map(k -> new BlobStoreFiles.File(k, new Date(), 1234L))    //TODO Add real details?
                .collect(Collectors.toList());
        BlobStoreFiles blobStoreFiles = new BlobStoreFiles();
        blobStoreFiles.add(files);
        return blobStoreFiles;
    }

    @Override
    public InputStream getBlob(String objectName) {
        logger.debug("get blob called in fake");
        byte[] data = uploads.get(objectName);
        return (data == null) ? null : new ByteArrayInputStream(data);
    }

    @Override
    public void uploadBlob(String objectName, InputStream inputStream) {
        try {
            logger.debug("upload blob called in fake");
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            IOUtils.copy(inputStream, byteArrayOutputStream);
            byte[] data = byteArrayOutputStream.toByteArray();
            uploads.put(objectName, data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
