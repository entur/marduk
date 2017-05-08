package no.rutebanken.marduk.geocoder.routes.pelias;

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.geocoder.GeoCoderConstants;
import no.rutebanken.marduk.repository.InMemoryBlobStoreRepository;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileInputStream;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = PeliasUpdateEsIndexRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
public class PeliasUpdateESIndexRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @Autowired
    private ModelCamelContext context;

    @Value("${elasticsearch.scratch.url:http4://es-scratch:9200}")
    private String elasticsearchScratchUrl;

    @Value("${tiamat.export.blobstore.subdirectory:tiamat}")
    private String blobStoreSubdirectoryForTiamatExport;

    @Value("${kartverket.blobstore.subdirectory:kartverket}")
    private String blobStoreSubdirectoryForKartverket;

    @EndpointInject(uri = "mock:es-scratch-bulk-index")
    protected MockEndpoint esScratchMock;

    @EndpointInject(uri = "mock:es-scratch-address-search")
    protected MockEndpoint esScratchAddressSearchMock;

    @Produce(uri = "direct:insertElasticsearchIndexData")
    protected ProducerTemplate insertESDataTemplate;
    @Autowired
    private InMemoryBlobStoreRepository inMemoryBlobStoreRepository;

    private static final String ADDRESS_SEARCH_RESPONSE = "{\"hits\":{\"total\":48,\"hits\":[{\"_index\":\"pelias\",\"_type\":\"address\",\"_id\":\"219137805\",\"_score\":12.170439,\"_source\":{\"center_point\":{\"lon\":8.085754906437227,\"lat\":58.175904836789755},\"parent\":{\"county_id\":[\"10\"],\"locality_id\":[\"1001\"],\"postalCode_id\":[\"4635\"],\"county\":[\"Vest-Agder\"],\"locality\":[\"Kristiansand\"],\"borough_id\":[\"10011703\"],\"borough\":[\"Hånes Vest\"],\"country_id\":[\"NOR\"]},\"name\":{\"default\":\"Vigvollåsen 39\"},\"alpha3\":\"NOR\",\"address_parts\":{\"zip\":\"4635\",\"number\":\"39\",\"street\":\"Vigvollåsen\",\"name\":\"Vigvollåsen\"},\"source\":\"kartverket\",\"source_id\":\"219137805\",\"category\":[\"Vegadresse\"],\"defaultName\":\"Vigvollåsen 39\",\"layer\":\"address\"}}]}}";

    @Test
    public void testInsertElasticsearchIndexDataSuccess() throws Exception {

        // Stub for elastic search scratch instance
        context.getRouteDefinition("pelias-invoke-bulk-command").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint(elasticsearchScratchUrl + "/_bulk")
                        .skipSendToOriginalEndpoint().to("mock:es-scratch-bulk-index");
            }
        });

        context.getRouteDefinition("pelias-create-street-from-addresses").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint(elasticsearchScratchUrl + "/pelias/address/_search")
                        .skipSendToOriginalEndpoint().to("mock:es-scratch-address-search");
            }
        });

        inMemoryBlobStoreRepository.uploadBlob(blobStoreSubdirectoryForKartverket + "/administrativeUnits/SosiTest.sos",
                new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/geocoder/sosi/SosiTest.sos")), false);
        inMemoryBlobStoreRepository.uploadBlob(blobStoreSubdirectoryForKartverket + "/placeNames/placenames.geojson",
                new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/geocoder/geojson/fylker.geojson")), false);
        inMemoryBlobStoreRepository.uploadBlob(blobStoreSubdirectoryForKartverket + "/addresses/addresses.csv",
                new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/geocoder/csv/addresses.csv")), false);
        inMemoryBlobStoreRepository.uploadBlob(blobStoreSubdirectoryForTiamatExport + "/tiamat/tiamat-export-latest.xml",
                new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/geocoder/netex/tiamat-export.xml")), false);


        esScratchMock.expectedMessageCount(5);

        esScratchAddressSearchMock.whenExchangeReceived(1, e -> e.getIn().setBody(ADDRESS_SEARCH_RESPONSE));
        context.start();

        Exchange e = insertESDataTemplate.request("direct:insertElasticsearchIndexData", ex -> {
        });

        Assert.assertEquals(GeoCoderConstants.PELIAS_ES_SCRATCH_STOP, e.getProperty(GeoCoderConstants.GEOCODER_NEXT_TASK));
        esScratchMock.assertIsSatisfied();
    }

    }
