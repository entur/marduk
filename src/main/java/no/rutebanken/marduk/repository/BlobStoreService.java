package no.rutebanken.marduk.repository;

import org.apache.camel.Header;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.domain.PageSet;
import org.jclouds.blobstore.domain.StorageMetadata;
import org.jclouds.blobstore.options.ListContainerOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.rest.ListFilesResponse;
import no.rutebanken.marduk.rest.ListFilesResponse.File;

@Repository(value = "blobStoreService")
public class BlobStoreService {

	@Autowired
	private BlobStore blobStore;

	@Value("${blobstore.containerName}")
	private String containerName;

	public ListFilesResponse getFiles(@Header(value = Constants.CHOUETTE_REFERENTIAL) String referential) {

		ListFilesResponse rsp = new ListFilesResponse();

		ListContainerOptions options = new ListContainerOptions()
				.inDirectory(Constants.BLOBSTORE_PATH_INBOUND_RECEIVED + referential).recursive().withDetails();
		PageSet<? extends StorageMetadata> list = blobStore.list(containerName, options);
		for (StorageMetadata x : list) {
			File f = new File(x.getName(), x.getLastModified(), x.getSize());

			rsp.add(f);
		}

		return rsp;
	}

}
