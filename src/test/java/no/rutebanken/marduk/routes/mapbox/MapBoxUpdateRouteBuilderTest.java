package no.rutebanken.marduk.routes.mapbox;

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.geocoder.routes.tiamat.TiamatGeoCoderExportRouteBuilder;
import no.rutebanken.marduk.repository.InMemoryBlobStoreRepository;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;

import java.io.File;
import java.io.FileInputStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static no.rutebanken.marduk.routes.mapbox.MapBoxUpdateRouteBuilder.*;
import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = MapBoxUpdateRouteBuilder.class,
        properties = {
                "spring.main.sources=no.rutebanken.marduk.test",
                "mapbox.api.url=http4://localhost:${wiremock.server.port}",
                "mapbox.upload.status.poll.delay=0"
        })
@AutoConfigureWireMock(port = 0)
public class MapBoxUpdateRouteBuilderTest extends MardukRouteBuilderIntegrationTestBase {

    public static final String TILESET_ID = "someId";
    public static final String RETRIEVE_CREDENTIALS_PATH_PATTERN = "/uploads/v1/(\\w+)/credentials.*";
    public static final String UPLOAD_INITIATE_PATH_PATTERN = "/uploads/v1/\\w+\\?{1}access_token.*";
    public static final String UPLOAD_STATUS_PATH_PATTERN = "/uploads/v1/\\w+/" + TILESET_ID;

    public static final String MAPBOX_RESPONSE_NOT_COMPLETE = "{\"id\":\"" + TILESET_ID + "\", \"name\":\"tiamat.geojson\", \"complete\":false, \"error\":null, \"created\":\"2018-01-19T10:14:41.359Z\"," +
            " \"modified\":\"2018-01-19T10:14:41.359Z\", \"tileset\":\"tilesetname\", \"owner\":\"owner\", \"progress\":0}";

    public static final String MAPBOX_RESPONSE_ERROR = "{\"id\":\"" + TILESET_ID + "\", \"name\":\"tiamat.geojson\", \"complete\":false, \"error\":\"Failure!\", \"created\":\"2018-01-19T10:14:41.359Z\"," +
            " \"modified\":\"2018-01-19T10:14:41.359Z\", \"tileset\":\"tilesetname\", \"owner\":\"owner\", \"progress\":0}";

    public static final String MAPBOX_RESPONSE_COMPLETE = "{\"id\":\"" + TILESET_ID + "\", \"name\":\"tiamat.geojson\", \"complete\":true, \"error\":null, \"created\":\"2018-01-19T10:14:41.359Z\"," +
            " \"modified\":\"2018-01-19T10:14:41.359Z\", \"tileset\":\"tilesetname\", \"owner\":\"owner\", \"progress\":1}";
    public static final String MAPBOX_CREDENTIALS_RESPONSE = "{ \"bucket\": \"bucket\", \"key\": \"key\", \"accessKeyId\": \"accessKeyId\", " +
            " \"secretAccessKey\": \"secretAKey\", \"sessionToken\": \"sestoken\", \"url\": \"http://localhost:0000\" }";


    @Autowired
    private ModelCamelContext context;

    @Value("${tiamat.export.blobstore.subdirectory:tiamat/geocoder}")
    private String blobStoreSubdirectoryForTiamatGeoCoderExport;

    @Produce(uri = "direct:uploadTiamatToMapboxAsGeoJson")
    protected ProducerTemplate producerTemplate;

    @Autowired
    private InMemoryBlobStoreRepository inMemoryBlobStoreRepository;

    @Before
    public void before() throws Exception {
        replaceEndpoint("mapbox-convert-upload-tiamat-data", "direct:uploadMapboxDataAws", "mock:uploadMapboxDataAws");
        inMemoryBlobStoreRepository.uploadBlob(blobStoreSubdirectoryForTiamatGeoCoderExport + "/" + TiamatGeoCoderExportRouteBuilder.TIAMAT_EXPORT_LATEST_FILE_NAME,
                new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/routes/netex/stops.zip")), false);
        context.start();
    }

    /**
     * Test that the finished state is set upon successful upload
     *
     * @throws Exception
     */
    @Test
    public void testMapLayerDataSuccess() throws Exception {
        stubCredentials();
        stubInitiateUpload();
        stubSuccess();
        Exchange e = producerTemplate.request("direct:uploadTiamatToMapboxAsGeoJson", System.out::println);
        assertState(e, STATE_FINISHED);
    }

    /**
     * Test that error response from mapbox is handled
     */
    @Test
    public void testMapLayerDataError() throws Exception {
        stubCredentials();
        stubInitiateUpload();
        stubError();
        Exchange e = producerTemplate.request("direct:uploadTiamatToMapboxAsGeoJson", System.out::println);
        assertState(e, STATE_ERROR);
    }

    /**
     * Test that a state is set when giving up checking the status
     */
    @Test
    public void testMapLayerDataTimeout() throws Exception {
        stubCredentials();
        stubInitiateUpload();
        // Always return not complete
        stubNotComplete();
        Exchange e = producerTemplate.request("direct:uploadTiamatToMapboxAsGeoJson", System.out::println);
        assertState(e, STATE_TIMEOUT);
    }


    private void assertState(Exchange e, String state) {
        assertThat(e.getProperties().get(PROPERTY_STATE)).isEqualTo(state);
    }

    private void stubError() {
        stubFor(post(urlMatching(UPLOAD_INITIATE_PATH_PATTERN))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(MAPBOX_RESPONSE_ERROR)));
    }

    private void stubNotComplete() {
        stubFor(get(urlPathMatching(UPLOAD_STATUS_PATH_PATTERN))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(MAPBOX_RESPONSE_NOT_COMPLETE)));
    }

    private void stubSuccess() {
        stubFor(get(urlPathMatching(UPLOAD_STATUS_PATH_PATTERN))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(MAPBOX_RESPONSE_COMPLETE)));
    }

    private void stubCredentials() {
        stubFor(post(urlPathMatching(RETRIEVE_CREDENTIALS_PATH_PATTERN))
                .willReturn(
                        aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(MAPBOX_CREDENTIALS_RESPONSE)));
    }

    public void stubInitiateUpload() {
        stubFor(post(urlMatching(UPLOAD_INITIATE_PATH_PATTERN))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(MAPBOX_RESPONSE_NOT_COMPLETE)));
    }
}