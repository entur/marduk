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
import org.apache.camel.test.spring.CamelSpringRunner;
import org.apache.camel.test.spring.UseAdviceWith;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.FileInputStream;

@RunWith(CamelSpringRunner.class)
@SpringBootTest(classes = PeliasUpdateEsIndexRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles({"default", "in-memory-blobstore"})
@UseAdviceWith
public class PeliasUpdateESIndexRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

	@Autowired
	private ModelCamelContext context;

	@Value("${elasticsearch.scratch.url:http4://es-scratch:9200}")
	private String elasticsearchScratchUrl;

	@Value("${tiamat.export.blobstore.subdirectory:tiamat}")
	private String blobStoreSubdirectoryForTiamatExport;

	@Value("${kartverket.place.names.blobstore.subdirectory:kartverket}")
	private String blobStoreSubdirectoryForKartverket;

	@EndpointInject(uri = "mock:es-scratch")
	protected MockEndpoint esScratchMock;

	@Produce(uri = "direct:insertElasticsearchIndexData")
	protected ProducerTemplate insertESDataTemplate;
	@Autowired
	private InMemoryBlobStoreRepository inMemoryBlobStoreRepository;


	@Test
	public void testInsertElasticsearchIndexDataSuccess() throws Exception {

		// Stub for elastic search scratch instance
		context.getRouteDefinition("pelias-invoke-bulk-command").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint(elasticsearchScratchUrl + "/_bulk")
						.skipSendToOriginalEndpoint().to("mock:es-scratch");
			}
		});

		inMemoryBlobStoreRepository.uploadBlob(blobStoreSubdirectoryForKartverket + "/administrativeUnits/fylker.geojson",
				new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/geocoder/geojson/fylker.geojson")), false);
		inMemoryBlobStoreRepository.uploadBlob(blobStoreSubdirectoryForKartverket + "/placeNames/placenames.geojson",
				new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/geocoder/geojson/fylker.geojson")), false);
		inMemoryBlobStoreRepository.uploadBlob(blobStoreSubdirectoryForKartverket + "/addresses/addresses.csv",
				new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/geocoder/csv/addresses.csv")), false);
		inMemoryBlobStoreRepository.uploadBlob(blobStoreSubdirectoryForTiamatExport + "/tiamat/tiamat-export-latest.xml",
				new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/geocoder/netex/tiamat-export.xml")), false);

		esScratchMock.expectedMessageCount(4);

		context.start();

		Exchange e = insertESDataTemplate.request("direct:insertElasticsearchIndexData", ex -> {
		});

		Assert.assertEquals(GeoCoderConstants.PELIAS_ES_SCRATCH_STOP, e.getProperty(GeoCoderConstants.GEOCODER_NEXT_TASK));
		esScratchMock.assertIsSatisfied();
	}
}
