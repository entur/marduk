package no.rutebanken.marduk.services;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.repository.BlobStoreRepository;
import org.apache.camel.Exchange;
import org.apache.camel.Header;

import java.io.InputStream;
import java.util.Collection;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;

public abstract class AbstractBlobStoreService {

    protected final BlobStoreRepository repository;

    private final String containerName;

    protected AbstractBlobStoreService(String containerName, BlobStoreRepository repository) {
        this.containerName = containerName;
        this.repository = repository;
        this.repository.setContainerName(containerName);
    }

    public BlobStoreFiles listBlobsInFolder(@Header(value = Exchange.FILE_PARENT) String folder) {
        return repository.listBlobs(folder + "/");
    }

    public BlobStoreFiles listBlobsInFolders(@Header(value = Constants.FILE_PARENT_COLLECTION) Collection<String> folders) {
        return repository.listBlobs(folders);
    }

    public BlobStoreFiles listBlobs(@Header(value = Constants.CHOUETTE_REFERENTIAL) String referential) {
        return repository.listBlobs(Constants.BLOBSTORE_PATH_INBOUND + referential + "/");
    }

    public BlobStoreFiles listBlobsFlat(@Header(value = Constants.CHOUETTE_REFERENTIAL) String referential) {
        return repository.listBlobsFlat(Constants.BLOBSTORE_PATH_INBOUND + referential + "/");
    }

    public BlobStoreFiles.File findBlob(@Header(value = Constants.FILE_PREFIX) String prefix) {
        BlobStoreFiles blobStoreFiles = repository.listBlobs(prefix);
        if(blobStoreFiles.getFiles().isEmpty()) {
            return null;
        } else if(blobStoreFiles.getFiles().size() > 1) {
            throw new MardukException("Found multiple files matching the prefix " + prefix);
        }
        return blobStoreFiles.getFiles().getFirst();
    }

    public InputStream getBlob(@Header(value = Constants.FILE_HANDLE) String name) {
        return repository.getBlob(name);
    }

    public void uploadBlob(@Header(value = Constants.FILE_HANDLE) String name,
                           InputStream inputStream, Exchange exchange) {
        long generation = repository.uploadBlob(name, inputStream);
        exchange.getIn().setHeader(Constants.FILE_VERSION, generation);
    }

    public void copyBlobInBucket(@Header(value = Constants.FILE_HANDLE) String sourceName, @Header(value = Constants.TARGET_FILE_HANDLE) String targetName) {
        repository.copyBlob(containerName, sourceName, containerName, targetName);
    }

    public void copyBlobToAnotherBucket(@Header(value = Constants.FILE_HANDLE) String sourceName,
                                        @Header(value = Constants.TARGET_CONTAINER) String targetContainerName,
                                        @Header(value = Constants.TARGET_FILE_HANDLE) String targetName) {
        repository.copyBlob(containerName, sourceName, targetContainerName, targetName);
    }

    public void copyVersionedBlobToAnotherBucket(@Header(value = Constants.FILE_HANDLE) String sourceName,
                                        @Header(value = Constants.FILE_VERSION) Long sourceVersion,
                                        @Header(value = Constants.TARGET_CONTAINER) String targetContainerName,
                                        @Header(value = Constants.TARGET_FILE_HANDLE) String targetName) {
        repository.copyVersionedBlob(containerName, sourceName, sourceVersion, targetContainerName, targetName);
    }

    public void copyAllBlobs(@Header(value = Exchange.FILE_PARENT) String sourceFolder, @Header(value = Constants.TARGET_CONTAINER) String targetContainerName, @Header(value = Constants.TARGET_FILE_PARENT) String targetFolder) {
        repository.copyAllBlobs(containerName, sourceFolder, targetContainerName, targetFolder);
    }

    public boolean deleteBlob(@Header(value = FILE_HANDLE) String name) {
        return repository.delete(name);
    }

    public boolean deleteAllBlobsInFolder(@Header(value = Exchange.FILE_PARENT) String folder) {
        return repository.deleteAllFilesInFolder(folder);
    }

}
