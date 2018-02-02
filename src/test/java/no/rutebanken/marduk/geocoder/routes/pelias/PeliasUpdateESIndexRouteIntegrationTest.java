/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,classes = PeliasUpdateEsIndexRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
public class PeliasUpdateESIndexRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

	@Autowired
	private ModelCamelContext context;

	@Value("${elasticsearch.scratch.url:http4://es-scratch:9200}")
	private String elasticsearchScratchUrl;

	@Value("${tiamat.export.blobstore.subdirectory:tiamat/geocoder}")
	private String blobStoreSubdirectoryForTiamatGeoCoderExport;

	@Value("${kartverket.blobstore.subdirectory:kartverket}")
	private String blobStoreSubdirectoryForKartverket;

	@EndpointInject(uri = "mock:es-scratch")
	protected MockEndpoint esScratchMock;

	@EndpointInject(uri = "mock:es-scratch-admin-index")
	protected MockEndpoint esScratchAdminIndexMock;


	@Produce(uri = "direct:insertElasticsearchIndexData")
	protected ProducerTemplate insertESDataTemplate;
	@Autowired
	private InMemoryBlobStoreRepository inMemoryBlobStoreRepository;


	@Test
	public void testInsertElasticsearchIndexDataSuccess() throws Exception {

		// Stub for elastic search scratch instance
		context.getRouteDefinition("pelias-delete-index-if-exists").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint(elasticsearchScratchUrl + "/pelias")
						.skipSendToOriginalEndpoint().to("mock:es-scratch-admin-index");
			}
		});
		context.getRouteDefinition("pelias-create-index").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint(elasticsearchScratchUrl + "/pelias")
						.skipSendToOriginalEndpoint().to("mock:es-scratch-admin-index");
			}
		});

		context.getRouteDefinition("pelias-invoke-bulk-command").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint(elasticsearchScratchUrl + "/_bulk")
						.skipSendToOriginalEndpoint().to("mock:es-scratch");
			}
		});

		inMemoryBlobStoreRepository.uploadBlob(blobStoreSubdirectoryForKartverket + "/administrativeUnits/SosiTest.sos",
				new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/geocoder/sosi/SosiTest.sos")), false);
		inMemoryBlobStoreRepository.uploadBlob(blobStoreSubdirectoryForKartverket + "/placeNames/placenames.sos",
				new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/geocoder/sosi/placeNames.sos")), false);
		inMemoryBlobStoreRepository.uploadBlob(blobStoreSubdirectoryForKartverket + "/addresses/addresses.csv",
				new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/geocoder/csv/addresses.csv")), false);
		inMemoryBlobStoreRepository.uploadBlob(blobStoreSubdirectoryForTiamatGeoCoderExport + "/tiamat/tiamat-export-latest.xml",
				new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/geocoder/netex/tiamat-export.xml")), false);


		esScratchAdminIndexMock.expectedMessageCount(2);
		esScratchMock.expectedMessageCount(4);
		context.start();

		Exchange e = insertESDataTemplate.request("direct:insertElasticsearchIndexData", ex -> {
		});

		Assert.assertEquals(GeoCoderConstants.PELIAS_ES_SCRATCH_STOP, e.getProperty(GeoCoderConstants.GEOCODER_NEXT_TASK));
		esScratchAdminIndexMock.assertIsSatisfied();
		esScratchMock.assertIsSatisfied();

	}
}
