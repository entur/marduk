package no.rutebanken.marduk.google;


import com.google.cloud.AuthCredentials;
import com.google.cloud.Page;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.*;
import com.google.common.collect.ImmutableList;
import no.rutebanken.marduk.Constants;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Iterator;

public class GoogleApiTest {

    private static final String BUCKET_NAME = "marduk-test";
    private static final String FILE_NAME = "/home/tomgag/code/rutebanken/rutedata/input/ruter/gtfs/ruter.zip";


    public static Blob uploadSimple(Storage storage, String bucketName, String objectName,
                                             InputStream data, String contentType) throws IOException {
        BlobId blobId = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.builder(blobId)
                .acl(ImmutableList.of(Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER)))
                .contentType(contentType).build();
        return storage.create(blobInfo, data);
    }

    public static Iterator<Blob> list(Storage storage, String bucketName, String directory)
            throws IOException {
        Page<Blob> blobs = storage.list(bucketName, // Storage.BlobListOption.currentDirectory(),  //opposite of recursive...
                Storage.BlobListOption.prefix(directory));
        return blobs.iterateAll();
    }

    public static void downloadToOutputStream(Storage storage, String bucketName, String objectName,
                                              OutputStream data) throws IOException {
        BlobId blobId = BlobId.of(bucketName, objectName);
        Blob blob = storage.get(blobId);
        if (blob != null) {
            try (ReadChannel reader = blob.reader()) {
                WritableByteChannel channel = Channels.newChannel(data);
                ByteBuffer bytes = ByteBuffer.allocate(64 * 1024);
                while (reader.read(bytes) > 0) {
                    bytes.flip();
                    channel.write(bytes);
                    bytes.clear();
                }
            }
        } else {
            System.out.println("Could not find '" + blobId.name() + "'");
        }
    }

    public static void main(String[] args) throws Exception {
        StorageOptions options = StorageOptions.builder()
                .projectId("carbon-1287")
                .authCredentials(AuthCredentials.createForJson(
                        new FileInputStream("/home/tomgag/.ssh/Carbon-a4d50ca8176c.json"))).build();
        Storage storage = options.service();

        String directory = Constants.BLOBSTORE_PATH_INBOUND_RECEIVED;

        String objectName1 = directory + "ruter1.zip";

        byte[] data = "Yes, this works!".getBytes();
        Blob blob = uploadSimple(storage, BUCKET_NAME, objectName1, new ByteArrayInputStream(data), "application/octet-stream");
        System.out.println(blob.name() + " (size: " + blob.size() + ")");

        String objectName = directory + "ruter2.zip";

        Blob blob1 = uploadSimple(storage, BUCKET_NAME, objectName, new FileInputStream(FILE_NAME), "application/octet-stream");
        System.out.println(blob1.name() + " (size: " + blob1.size() + ")");

        list(storage, BUCKET_NAME, directory).forEachRemaining(blob2 -> System.out.println("*" + blob2.name() + " (size: " + blob2.size() + ")"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        downloadToOutputStream(storage, BUCKET_NAME, objectName1, out);
        System.out.println("Downloaded " + out.toByteArray().length + " bytes");
    }

}