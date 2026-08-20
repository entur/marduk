package no.rutebanken.marduk.routes.otp.otp2;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.OTP2_NETEX_GRAPH_DIR;
import static no.rutebanken.marduk.Constants.OTP_REMOTE_WORK_DIR;
import static no.rutebanken.marduk.Constants.TARGET_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;
import static no.rutebanken.marduk.Constants.TIMESTAMP;

class Otp2NetexGraphPublishingProcessorTest {

    @Test
    void testGetGraphCompatibilityVersion() {
        String graphCompatibilityVersion = Otp2NetexGraphPublishingProcessor.getGraphCompatibilityVersion("Graph-otp2-xxx.obj");
        Assertions.assertEquals("xxx", graphCompatibilityVersion);
    }

    @Test
    void testGetGraphCompatibilityVersionUnknownVersion() {
        String graphCompatibilityVersion = Otp2NetexGraphPublishingProcessor.getGraphCompatibilityVersion("Graph-otp2.obj");
        Assertions.assertEquals("unknown-version", graphCompatibilityVersion);
    }

    @Test
    void testProcessorNoFile() {
        CamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        Otp2NetexGraphPublishingProcessor otp2NetexGraphPublishingProcessor = new Otp2NetexGraphPublishingProcessor(null);
        Assertions.assertThrows(IllegalStateException.class, () -> otp2NetexGraphPublishingProcessor.process(exchange));
    }

    @Test
    void testProcessor() {
        CamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);

        String otpGraphsBucketName = "graphs-buket-name";
        String graphCompatibilityVersion = "xxx";
        String fileName = Constants.OTP2_GRAPH_OBJ_PREFIX + "-" + graphCompatibilityVersion + ".obj";
        BlobStoreFiles.File file = new BlobStoreFiles.File();
        String timestamp = "timestamp";
        file.setName(fileName);
        String remoteWorkDirectory = "remote-work-dir";

        exchange.getIn().setBody(file);
        exchange.setProperty(OTP_REMOTE_WORK_DIR, remoteWorkDirectory);
        exchange.setProperty(TIMESTAMP, timestamp);

        Otp2NetexGraphPublishingProcessor otp2NetexGraphPublishingProcessor = new Otp2NetexGraphPublishingProcessor(otpGraphsBucketName);
        otp2NetexGraphPublishingProcessor.process(exchange);

        Assertions.assertEquals(remoteWorkDirectory + "/" + fileName, exchange.getIn().getHeader(FILE_HANDLE, String.class));
        Assertions.assertEquals(OTP2_NETEX_GRAPH_DIR + "/" + graphCompatibilityVersion + "/" + timestamp + "-" + fileName, exchange.getIn().getHeader(TARGET_FILE_HANDLE, String.class));
        Assertions.assertEquals(otpGraphsBucketName, exchange.getIn().getHeader(TARGET_CONTAINER, String.class));


    }


}
