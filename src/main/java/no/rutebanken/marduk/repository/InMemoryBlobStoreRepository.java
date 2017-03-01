package no.rutebanken.marduk.repository;

import com.google.cloud.storage.Storage;
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
@Profile("in-memory-blobstore")
public class InMemoryBlobStoreRepository implements BlobStoreRepository {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	private Map<String, byte[]> blobs = new HashMap<>();

	@Override
	public BlobStoreFiles listBlobs(String prefix) {
		logger.debug("list blobs called in in-memory blob store");
		List<BlobStoreFiles.File> files = blobs.keySet().stream()
				                                  .filter(k -> k.startsWith(prefix))
				                                  .map(k -> new BlobStoreFiles.File(k, new Date(), 1234L))    //TODO Add real details?
				                                  .collect(Collectors.toList());
		BlobStoreFiles blobStoreFiles = new BlobStoreFiles();
		blobStoreFiles.add(files);
		return blobStoreFiles;
	}

	@Override
	public BlobStoreFiles listBlobsFlat(String prefix) {
		List<BlobStoreFiles.File> files = listBlobs(prefix).getFiles();
		List<BlobStoreFiles.File> result = files.stream().map(k -> new BlobStoreFiles.File(k.getName().replaceFirst(prefix + "/", ""), new Date(), 1234L)).collect(Collectors.toList());
		BlobStoreFiles blobStoreFiles = new BlobStoreFiles();
		blobStoreFiles.add(result);
		return blobStoreFiles;
	}

	@Override
	public InputStream getBlob(String objectName) {
		logger.debug("get blob called in in-memory blob store");
		byte[] data = blobs.get(objectName);
		return (data == null) ? null : new ByteArrayInputStream(data);
	}

	@Override
	public void uploadBlob(String objectName, InputStream inputStream, boolean makePublic) {
		try {
			logger.debug("upload blob called in in-memory blob store");
			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			IOUtils.copy(inputStream, byteArrayOutputStream);
			byte[] data = byteArrayOutputStream.toByteArray();
			blobs.put(objectName, data);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean delete(String objectName) {
		blobs.remove(objectName);
		return true;
	}

	@Override
	public void setStorage(Storage storage) {

	}

	@Override
	public void setContainerName(String containerName) {

	}

}
