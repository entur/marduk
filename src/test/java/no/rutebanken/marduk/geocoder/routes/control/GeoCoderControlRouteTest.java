package no.rutebanken.marduk.geocoder.routes.control;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.geocoder.GeoCoderConstants;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.spring.CamelSpringRunner;
import org.apache.camel.test.spring.UseAdviceWith;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.apache.camel.builder.Builder.constant;

@RunWith(CamelSpringRunner.class)
@SpringBootTest(classes = GeoCoderControlRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles({"default", "in-memory-blobstore"})
@UseAdviceWith
public class GeoCoderControlRouteTest extends MardukRouteBuilderIntegrationTestBase {

	@Autowired
	private ModelCamelContext context;

	@EndpointInject(uri = "mock:destination")
	protected MockEndpoint destination;

	@Produce(uri = "activemq:queue:GeoCoderQueue")
	protected ProducerTemplate geoCoderQueueTemplate;

	@Test
	public void testMessagesAreMergedAndTaskedOrderAccordingToPhase() throws Exception {
		destination.expectedMessageCount(4);

		GeoCoderTask task1 = task(GeoCoderTask.Phase.DOWNLOAD_SOURCE_DATA);
		GeoCoderTask task2 = task(GeoCoderTask.Phase.TIAMAT_UPDATE);
		GeoCoderTask task3 = task(GeoCoderTask.Phase.TIAMAT_EXPORT);
		GeoCoderTask task4 = task(GeoCoderTask.Phase.PELIAS_UPDATE);

		destination.expectedBodiesReceived(task1, task2, task3, task4);
		geoCoderQueueTemplate.sendBody(new GeoCoderTaskMessage(task3).toString());
		geoCoderQueueTemplate.sendBody(new GeoCoderTaskMessage(task2, task4).toString());
		geoCoderQueueTemplate.sendBody(new GeoCoderTaskMessage(task1).toString());

		context.start();

		destination.assertIsSatisfied();
	}

	@Test
	public void testOngoingTasksAreAllowedToComplete() throws Exception {
		GeoCoderTask ongoingInit = task(GeoCoderTask.Phase.PELIAS_UPDATE);
		ongoingInit.setSubStep(1);
		GeoCoderTask ongoingFinalStep = task(GeoCoderTask.Phase.PELIAS_UPDATE);
		ongoingFinalStep.setSubStep(2);
		GeoCoderTask earlierPhase = task(GeoCoderTask.Phase.TIAMAT_UPDATE);

		// First task is rescheduled for step 2
		destination.whenExchangeReceived(1, e -> e.setProperty(GeoCoderConstants.GEOCODER_NEXT_TASK, ongoingFinalStep));
		destination.expectedBodiesReceived(ongoingInit, ongoingFinalStep, earlierPhase);
		geoCoderQueueTemplate.sendBody(new GeoCoderTaskMessage(ongoingInit, earlierPhase).toString());

		context.start();

		destination.assertIsSatisfied();
	}

	@Test
	public void testTaskHeadersAreRehydratedToContext() throws Exception {

		String correlationID = "corrIdTest";
		GeoCoderTask task1 = task(GeoCoderTask.Phase.DOWNLOAD_SOURCE_DATA);
		task1.getHeaders().put(Constants.SYSTEM_STATUS_CORRELATION_ID, correlationID);

		destination.expectedHeaderReceived(Constants.SYSTEM_STATUS_CORRELATION_ID, correlationID);
		destination.expectedPropertyReceived(GeoCoderConstants.GEOCODER_CURRENT_TASK, task1);
		destination.expectedBodiesReceived(task1);
		geoCoderQueueTemplate.sendBody(new GeoCoderTaskMessage(task1).toString());

		context.start();

		destination.assertIsSatisfied();
	}


	private GeoCoderTask task(GeoCoderTask.Phase phase) {
		return new GeoCoderTask(phase, "mock:destination");
	}

}
