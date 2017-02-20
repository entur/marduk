//package no.rutebanken.marduk.geocoder.routes;
//
//import no.rutebanken.marduk.domain.BlobStoreFiles;
//import no.rutebanken.marduk.geocoder.routes.kartverket.AdministrativeUnitsDownloadRouteBuilder;
//import no.rutebanken.marduk.repository.InMemoryBlobStoreRepository;
//import org.apache.camel.Produce;
//import org.apache.camel.ProducerTemplate;
//import org.apache.camel.model.ModelCamelContext;
//import org.apache.camel.test.spring.CamelSpringRunner;
//import org.apache.camel.test.spring.UseAdviceWith;
//import org.junit.Assert;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.annotation.DirtiesContext;
//import org.springframework.test.context.ActiveProfiles;
//
//@RunWith(CamelSpringRunner.class)
//@SpringBootTest(classes = AdministrativeUnitsDownloadRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
//@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
//@ActiveProfiles({"default", "in-memory-blobstore"})
//@UseAdviceWith
//public class AdministrativeUnitsDownloadRouteBuilderTest {
//
//	@Autowired
//	private ModelCamelContext context;
//
//	@Autowired
//	private InMemoryBlobStoreRepository inMemoryBlobStoreRepository;
//
//
//	@Produce(uri = "activemq:queue:AdministrativeUnitsDownloadQueue")
//	protected ProducerTemplate downloadTemplate;
//
//
//	@Test
//	public void test() throws Exception {
//		context.start();
//
//
//		downloadTemplate.sendBody(null);
//
//Thread.sleep(5000); // TODO
//		BlobStoreFiles files = inMemoryBlobStoreRepository.listBlobs("");
//		Assert.assertEquals(15, files.getFiles().size());
//	}
////
//
//}
