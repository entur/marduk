package no.rutebanken.marduk.geocoder.routes.tiamat;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.geocoder.routes.tiamat.model.TiamatExportTask;
import no.rutebanken.marduk.geocoder.routes.tiamat.model.TiamatExportTaskType;
import no.rutebanken.marduk.geocoder.routes.tiamat.model.TiamatExportTasks;
import no.rutebanken.marduk.repository.InMemoryBlobStoreRepository;
import no.rutebanken.marduk.routes.etcd.InMemoryEtcdRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static no.rutebanken.marduk.Constants.QUERY_STRING;
import static no.rutebanken.marduk.Constants.TIAMAT_EXPORT_TASKS;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TiamatChangeLogExportRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
public class TiamatChangeLogExportRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @EndpointInject(uri = "mock:changeLogExport")
    protected MockEndpoint changeLogExportMock;


    @EndpointInject(uri = "mock:updateStatus")
    protected MockEndpoint statusQueueMock;

    @Produce(uri = "direct:processTiamatChangeLogExportTask")
    protected ProducerTemplate input;

    @Value("${tiamat.publish.export.blobstore.subdirectory:tiamat}")
    private String blobStoreSubdirectoryForTiamatExport;

    @Value("${tiamat.change.log.key.prefix:/v2/keys/dynamic/marduk/tiamat/change_log}")
    private String etcdKeyPrefix;

    @Autowired
    private InMemoryBlobStoreRepository inMemoryBlobStoreRepository;

    @Autowired
    private InMemoryEtcdRouteBuilder inMemoryEtcdRouteBuilder;

    @Before
    public void setUp() {
        changeLogExportMock.reset();
        statusQueueMock.reset();
        try {

            replaceEndpoint("tiamat-publish-export-process-changelog", "direct:exportChangedStopPlaces", "mock:changeLogExport");

            replaceEndpoint("tiamat-publish-export-process-changelog", "direct:updateStatus", "mock:updateStatus");


        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Test
    public void uploadBlobAndUpdateEtcdWhenContentIsChanged() throws Exception {
        inMemoryEtcdRouteBuilder.clean();
        statusQueueMock.expectedMessageCount(1);

        changeLogExportMock.whenAnyExchangeReceived(e -> {
            e.getOut().setBody("Content from Tiamat");
            e.getOut().setHeaders(e.getIn().getHeaders());
        });

        TiamatExportTask changeLogTask = new TiamatExportTask("testExport", "queryParam=XXX", TiamatExportTaskType.CHANGE_LOG);
        input.request("direct:processTiamatChangeLogExportTask", ex -> {
            ex.setProperty(TIAMAT_EXPORT_TASKS, new TiamatExportTasks(changeLogTask));
            ex.getIn().setHeader(Constants.SYSTEM_STATUS, JobEvent.builder().correlationId("1").jobDomain(JobEvent.JobDomain.TIAMAT).state(JobEvent.State.STARTED).action("EXPORT").build().toString());
        });

        statusQueueMock.assertIsSatisfied();
        changeLogExportMock.assertIsSatisfied();
        Assert.assertEquals(1, inMemoryBlobStoreRepository.listBlobsFlat(blobStoreSubdirectoryForTiamatExport + "/" + changeLogTask.getName()).getFiles().size());
        Assert.assertEquals(1, inMemoryEtcdRouteBuilder.values.get(etcdKeyPrefix + "/" + changeLogTask.getName() + "_cnt"));
        Assert.assertNotNull(inMemoryEtcdRouteBuilder.values.get(etcdKeyPrefix + "/" + changeLogTask.getName() + "_to"));
    }

    @Test
    public void doNotUpdateEtcdCntWhenNoChanges() throws Exception {
        TiamatExportTask changeLogTask = new TiamatExportTask("testExport", "?queryParam=XXX", TiamatExportTaskType.CHANGE_LOG);
        inMemoryEtcdRouteBuilder.clean();
        inMemoryEtcdRouteBuilder.values.put(etcdKeyPrefix + "/" + changeLogTask.getName() + "_to","PRE");
        statusQueueMock.expectedMessageCount(1);


        changeLogExportMock.whenAnyExchangeReceived(e -> {
            Assert.assertTrue(e.getIn().getHeader(QUERY_STRING, String.class).startsWith(changeLogTask.getQueryString()));
            e.getOut().setBody(null);
            e.getOut().setHeaders(e.getIn().getHeaders());
        });


        input.request("direct:processTiamatChangeLogExportTask", ex -> {
            ex.setProperty(TIAMAT_EXPORT_TASKS, new TiamatExportTasks(changeLogTask));
            ex.getIn().setHeader(Constants.SYSTEM_STATUS, JobEvent.builder().correlationId("1").jobDomain(JobEvent.JobDomain.TIAMAT).state(JobEvent.State.STARTED).action("EXPORT").build().toString());
        });

        statusQueueMock.assertIsSatisfied();
        changeLogExportMock.assertIsSatisfied();
        Assert.assertEquals(0, inMemoryBlobStoreRepository.listBlobsFlat(blobStoreSubdirectoryForTiamatExport + "/" + changeLogTask.getName()).getFiles().size());
        Assert.assertNull(inMemoryEtcdRouteBuilder.values.get(etcdKeyPrefix + "/" + changeLogTask.getName() + "_cnt"));
        Assert.assertEquals("To value should be unchanged when no changes are found","PRE",inMemoryEtcdRouteBuilder.values.get(etcdKeyPrefix + "/" + changeLogTask.getName() + "_to"));
    }

}