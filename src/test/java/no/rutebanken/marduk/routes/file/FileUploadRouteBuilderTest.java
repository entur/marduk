package no.rutebanken.marduk.routes.file;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.domain.Provider;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.mockito.Mockito.when;

class FileUploadRouteBuilderTest extends MardukRouteBuilderIntegrationTestBase {

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @EndpointInject("mock:uploadInternalBlob")
    protected MockEndpoint uploadInternalBlob;

    @EndpointInject("mock:processFileQueue")
    protected MockEndpoint processFileQueue;

    @Produce("direct:uploadFileAndStartImport")
    protected ProducerTemplate uploadFileAndStartImport;
    private Map<String, Object> headers;

    @BeforeEach
    void setup() throws Exception {
        AdviceWith.adviceWith(context, "upload-file-and-start-import", a -> {
            a.interceptSendToEndpoint("direct:updateStatus")
                    .skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");
            a.interceptSendToEndpoint("direct:uploadInternalBlob")
                    .skipSendToOriginalEndpoint()
                    .to("mock:uploadInternalBlob");

            a.weaveByToUri("google-pubsub:(.*):ProcessFileQueue")
                    .replace()
                    .to("mock:processFileQueue");
        });

        headers = Map.of(
                Constants.PROVIDER_ID, 2,
                Constants.CHOUETTE_REFERENTIAL, "RUT",
                Constants.CORRELATION_ID, "correlationId");

    }


    @Test
    void testUploadAndStartImport() throws Exception {
        when(providerRepository.getProvider(2L)).thenReturn(provider(true));
        InputStream testFile = getTestNetexArchiveAsStream();

        updateStatus.expectedMessageCount(1);
        uploadInternalBlob.expectedMessageCount(1);
        processFileQueue.expectedMessageCount(1);

        context.start();

        uploadFileAndStartImport.sendBodyAndHeaders(testFile, headers);
        updateStatus.assertIsSatisfied();
        uploadInternalBlob.assertIsSatisfied();
        processFileQueue.assertIsSatisfied();
    }

    @Test
    void testUploadAndDoNotStartImport() throws Exception {
        when(providerRepository.getProvider(2L)).thenReturn(provider(false));
        InputStream testFile = getTestNetexArchiveAsStream();

        updateStatus.expectedMessageCount(1);
        uploadInternalBlob.expectedMessageCount(1);
        processFileQueue.expectedMessageCount(0);

        context.start();

        uploadFileAndStartImport.sendBodyAndHeaders(testFile, headers);
        updateStatus.assertIsSatisfied();
        uploadInternalBlob.assertIsSatisfied();
        processFileQueue.assertIsSatisfied();
    }


    private static Provider provider(boolean enableAutoImport) throws IOException {
        Provider provider = Provider.create(IOUtils.toString(new FileReader(
                "src/test/resources/no/rutebanken/marduk/providerRepository/provider2.json")));
        provider.getChouetteInfo().setEnableAutoImport(enableAutoImport);
        return provider;
    }

}