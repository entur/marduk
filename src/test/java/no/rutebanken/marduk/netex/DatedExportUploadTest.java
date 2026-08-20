package no.rutebanken.marduk.netex;

import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.services.MardukPublicBlobStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.TARGET_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatedExportUploadTest {

    private static final String EXPORT_HANDLE = "outbound/netex/rb_rut-aggregated-netex.zip";
    private static final String PUBLIC_CONTAINER = "marduk";
    private static final String EXCHANGE_CONTAINER = "marduk-exchange";

    private InMemoryMardukBlobStoreRepository publicRepository;
    private DatedExportUpload datedExportUpload;

    @BeforeEach
    void setUp() {
        publicRepository = new InMemoryMardukBlobStoreRepository(new ConcurrentHashMap<>());
        datedExportUpload = new DatedExportUpload(
                new MardukPublicBlobStoreService(PUBLIC_CONTAINER, publicRepository),
                "outbound/dated",
                EXCHANGE_CONTAINER);
        publicRepository.uploadBlob(
                EXPORT_HANDLE, new ByteArrayInputStream("merged".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void theCurrentExportIsCopiedIntoTheExchangeBucketUnderADatedName() {
        MardukMessage message = new MardukMessage().setHeader(CHOUETTE_REFERENTIAL, "rb_rut");

        datedExportUpload.copyDatedExport(message);

        assertEquals(EXPORT_HANDLE, message.getHeader(FILE_HANDLE, String.class));
        assertEquals(EXCHANGE_CONTAINER, message.getHeader(TARGET_CONTAINER, String.class));
        String target = message.getHeader(TARGET_FILE_HANDLE, String.class);
        // The timestamp is what keeps successive exports of one referential from overwriting each other.
        assertTrue(target.matches("outbound/dated/rb_rut-\\d{17}\\.zip"),
                "unexpected dated file name " + target);

        publicRepository.setContainerName(EXCHANGE_CONTAINER);
        assertNotNull(publicRepository.getBlob(target), "the dated copy is not in the exchange bucket");
    }
}
