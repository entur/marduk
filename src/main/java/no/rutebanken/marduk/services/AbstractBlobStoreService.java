package no.rutebanken.marduk.services;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.repository.MardukBlobStoreRepository;

import java.io.InputStream;
import java.util.Collection;

/**
 * Blob operations on one bucket.
 *
 * <p>The parameters used to carry {@code @Header} annotations so Camel could bind them from the message.
 * They are now ordinary parameters and the caller reads what it needs off its own message, which is the
 * point: the binding was invisible at the call site, so a renamed header produced a null argument rather
 * than a compile error.
 */
public abstract class AbstractBlobStoreService {

    protected final MardukBlobStoreRepository repository;

    private final String containerName;

    protected AbstractBlobStoreService(String containerName, MardukBlobStoreRepository repository) {
        this.containerName = containerName;
        this.repository = repository;
        this.repository.setContainerName(containerName);
    }

    public BlobStoreFiles listBlobsInFolder(String folder) {
        return repository.listBlobs(folder + "/");
    }

    public BlobStoreFiles listBlobsInFolders(Collection<String> folders) {
        return repository.listBlobs(folders);
    }

    public BlobStoreFiles listBlobs(String referential) {
        return repository.listBlobs(Constants.BLOBSTORE_PATH_INBOUND + referential + "/");
    }

    public BlobStoreFiles listBlobsFlat(String referential) {
        return repository.listBlobsFlat(Constants.BLOBSTORE_PATH_INBOUND + referential + "/");
    }

    public BlobStoreFiles.File findBlob(String prefix) {
        BlobStoreFiles blobStoreFiles = repository.listBlobs(prefix);
        if (blobStoreFiles.getFiles().isEmpty()) {
            return null;
        } else if (blobStoreFiles.getFiles().size() > 1) {
            throw new MardukException("Found multiple files matching the prefix " + prefix);
        }
        return blobStoreFiles.getFiles().getFirst();
    }

    public InputStream getBlob(String name) {
        return repository.getBlob(name);
    }

    public boolean blobExists(String name) {
        return repository.exist(name);
    }

    /**
     * @return the generation of the uploaded blob, which the caller records as
     *         {@code RutebankenFileVersion} so a later copy can name this exact version
     */
    public long uploadBlob(String name, InputStream inputStream) {
        return repository.uploadBlob(name, inputStream);
    }

    public void uploadBlobWithoutVersionHeader(String name, InputStream inputStream) {
        repository.uploadBlob(name, inputStream);
    }

    public void copyBlobInBucket(String sourceName, String targetName) {
        repository.copyBlob(containerName, sourceName, containerName, targetName);
    }

    public void copyBlobToAnotherBucket(String sourceName, String targetContainerName, String targetName) {
        repository.copyBlob(containerName, sourceName, targetContainerName, targetName);
    }

    public void copyBlobFromAnotherBucket(String sourceContainerName, String sourceName, String targetName) {
        repository.copyBlob(sourceContainerName, sourceName, containerName, targetName);
    }

    public void copyVersionedBlobToAnotherBucket(
            String sourceName, Long sourceVersion, String targetContainerName, String targetName) {
        repository.copyVersionedBlob(containerName, sourceName, sourceVersion, targetContainerName, targetName);
    }

    public void copyAllBlobs(String sourceFolder, String targetContainerName, String targetFolder) {
        repository.copyAllBlobs(containerName, sourceFolder, targetContainerName, targetFolder);
    }

    public boolean deleteBlob(String name) {
        return repository.delete(name);
    }

    public boolean deleteAllBlobsInFolder(String folder) {
        return repository.deleteAllFilesInFolder(folder);
    }

}
