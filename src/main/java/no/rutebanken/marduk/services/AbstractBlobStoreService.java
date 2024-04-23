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

    public BlobStoreFiles listBlobsInFolder(@Header(value = Exchange.FILE_PARENT) String folder, Exchange exchange) {
        return repository.listBlobs(folder + "/");
    }

    public BlobStoreFiles listBlobsInFolders(@Header(value = Constants.FILE_PARENT_COLLECTION) Collection<String> folders, Exchange exchange) {
        return repository.listBlobs(folders);
    }

    public BlobStoreFiles listBlobs(@Header(value = Constants.CHOUETTE_REFERENTIAL) String referential, Exchange exchange) {
        return repository.listBlobs(Constants.BLOBSTORE_PATH_INBOUND + referential + "/");
    }

    public BlobStoreFiles listBlobsFlat(@Header(value = Constants.CHOUETTE_REFERENTIAL) String referential, Exchange exchange) {
        return repository.listBlobsFlat(Constants.BLOBSTORE_PATH_INBOUND + referential + "/");
    }

    public BlobStoreFiles.File findBlob(@Header(value = Constants.FILE_PREFIX) String prefix, Exchange exchange) {
        BlobStoreFiles blobStoreFiles = repository.listBlobs(prefix);
        if(blobStoreFiles.getFiles().isEmpty()) {
            return null;
        } else if(blobStoreFiles.getFiles().size() > 1) {
            throw new MardukException("Found multiple files matching the prefix " + prefix);
        }
        return blobStoreFiles.getFiles().getFirst();
    }

    public InputStream getBlob(@Header(value = Constants.FILE_HANDLE) String name, Exchange exchange) {
        return repository.getBlob(name);
    }

    public void uploadBlob(@Header(value = Constants.FILE_HANDLE) String name,
                           @Header(value = Constants.BLOBSTORE_MAKE_BLOB_PUBLIC) boolean makePublic, InputStream inputStream, Exchange exchange) {
        long generation = repository.uploadBlob(name, inputStream, makePublic);
        exchange.getIn().setHeader(Constants.FILE_VERSION, generation);
    }

    public void copyBlobInBucket(@Header(value = Constants.FILE_HANDLE) String sourceName, @Header(value = Constants.TARGET_FILE_HANDLE) String targetName, @Header(value = Constants.BLOBSTORE_MAKE_BLOB_PUBLIC) boolean makePublic, Exchange exchange) {
        repository.copyBlob(containerName, sourceName, containerName, targetName, makePublic);
    }

    public void copyBlobToAnotherBucket(@Header(value = Constants.FILE_HANDLE) String sourceName,
                                        @Header(value = Constants.TARGET_CONTAINER) String targetContainerName,
                                        @Header(value = Constants.TARGET_FILE_HANDLE) String targetName,
                                        @Header(value = Constants.BLOBSTORE_MAKE_BLOB_PUBLIC) boolean makePublic,
                                        Exchange exchange) {
        repository.copyBlob(containerName, sourceName, targetContainerName, targetName, makePublic);
    }

    public void copyVersionedBlobToAnotherBucket(@Header(value = Constants.FILE_HANDLE) String sourceName,
                                        @Header(value = Constants.FILE_VERSION) Long sourceVersion,
                                        @Header(value = Constants.TARGET_CONTAINER) String targetContainerName,
                                        @Header(value = Constants.TARGET_FILE_HANDLE) String targetName,
                                        @Header(value = Constants.BLOBSTORE_MAKE_BLOB_PUBLIC) boolean makePublic,
                                        Exchange exchange) {
        repository.copyVersionedBlob(containerName, sourceName, sourceVersion, targetContainerName, targetName, makePublic);
    }

    public void copyAllBlobs(@Header(value = Exchange.FILE_PARENT) String sourceFolder, @Header(value = Constants.TARGET_CONTAINER) String targetContainerName, @Header(value = Constants.TARGET_FILE_PARENT) String targetFolder, @Header(value = Constants.BLOBSTORE_MAKE_BLOB_PUBLIC) boolean makePublic, Exchange exchange) {
        repository.copyAllBlobs(containerName, sourceFolder, targetContainerName, targetFolder, makePublic);
    }

    public boolean deleteBlob(@Header(value = FILE_HANDLE) String name, Exchange exchange) {
        return repository.delete(name);
    }

    public boolean deleteAllBlobsInFolder(@Header(value = Exchange.FILE_PARENT) String folder, Exchange exchange) {
        return repository.deleteAllFilesInFolder(folder);
    }

}
