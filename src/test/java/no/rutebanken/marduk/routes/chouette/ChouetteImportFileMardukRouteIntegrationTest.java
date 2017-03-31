package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.repository.InMemoryBlobStoreRepository;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.apache.camel.*;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.test.spring.CamelSpringRunner;
import org.apache.camel.test.spring.UseAdviceWith;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = ChouetteImportRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ChouetteImportFileMardukRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @Autowired
    private ModelCamelContext context;

    @Autowired
    private InMemoryBlobStoreRepository inMemoryBlobStoreRepository;

    @EndpointInject(uri = "mock:chouetteCreateImport")
    protected MockEndpoint chouetteCreateImport;

    @EndpointInject(uri = "mock:pollJobStatus")
    protected MockEndpoint pollJobStatus;

    @EndpointInject(uri = "mock:chouetteGetJobsForProvider")
    protected MockEndpoint chouetteGetJobs;

    @EndpointInject(uri = "mock:processImportResult")
    protected MockEndpoint processActionReportResult;

    @EndpointInject(uri = "mock:chouetteValidationQueue")
    protected MockEndpoint chouetteValidationQueue;

    @EndpointInject(uri = "mock:checkScheduledJobsBeforeTriggeringNextAction")
    protected MockEndpoint checkScheduledJobsBeforeTriggeringNextAction;

    @EndpointInject(uri = "mock:updateStatus")
    protected MockEndpoint updateStatus;

    @Produce(uri = "activemq:queue:ProcessFileQueue")
    protected ProducerTemplate importTemplate;

    @Produce(uri = "direct:processImportResult")
    protected ProducerTemplate processImportResultTemplate;

    @Produce(uri = "direct:checkScheduledJobsBeforeTriggeringNextAction")
    protected ProducerTemplate triggerJobListTemplate;

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Value("${nabu.rest.service.url}")
    private String nabuUrl;

    @Before
    public void setUp() throws IOException {
        super.setUp();
        chouetteCreateImport.reset();
        pollJobStatus.reset();
        chouetteGetJobs.reset();
        processActionReportResult.reset();
        chouetteValidationQueue.reset();
        checkScheduledJobsBeforeTriggeringNextAction.reset();
        updateStatus.reset();
    }

    @Test
    public void testImportFileToDataspace() throws Exception {

        String filename = "ruter_fake_data.zip";
        String pathname = "src/test/resources/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip";

        //populate fake blob repo
        inMemoryBlobStoreRepository.uploadBlob("rut/" + filename, new FileInputStream(new File(pathname)), false);

        // Mock initial call to Chouette to import job
        context.getRouteDefinition("chouette-send-import-job").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/importer/regtopp")
                        .skipSendToOriginalEndpoint().to("mock:chouetteCreateImport");
            }
        });

        // Mock job polling route - AFTER header validatio (to ensure that we send correct headers in test as well
        context.getRouteDefinition("chouette-validate-job-status-parameters").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("direct:checkJobStatus").skipSendToOriginalEndpoint()
                        .to("mock:pollJobStatus");
            }
        });

        // Mock update status calls
        context.getRouteDefinition("chouette-process-import-status").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint()
                        .to("mock:updateStatus");
                interceptSendToEndpoint("direct:checkScheduledJobsBeforeTriggeringNextAction").skipSendToOriginalEndpoint()
                        .to("mock:checkScheduledJobsBeforeTriggeringNextAction");
            }
        });

        // we must manually start when we are done with all the advice with
        context.start();

        // 1 initial import call
        chouetteCreateImport.expectedMessageCount(1);
        chouetteCreateImport.returnReplyHeader("Location", new SimpleExpression(
                                                                                       chouetteUrl.replace("http4://", "http://") + "/chouette_iev/referentials/rut/scheduled_jobs/1"));


        pollJobStatus.expectedMessageCount(1);


        updateStatus.expectedMessageCount(1);
        checkScheduledJobsBeforeTriggeringNextAction.expectedMessageCount(1);


        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Constants.PROVIDER_ID, "2");
        headers.put(Constants.FILE_NAME, filename);
        headers.put(Constants.CORRELATION_ID, "corr_id");
        headers.put(Constants.FILE_HANDLE, "rut/" + filename);
        importTemplate.sendBodyAndHeaders(null, headers);

        chouetteCreateImport.assertIsSatisfied();
        pollJobStatus.assertIsSatisfied();

        Exchange exchange = pollJobStatus.getReceivedExchanges().get(0);
        exchange.getIn().setHeader("action_report_result", "OK");
        exchange.getIn().setHeader("validation_report_result", "OK");
        processImportResultTemplate.send(exchange);

        checkScheduledJobsBeforeTriggeringNextAction.assertIsSatisfied();
        updateStatus.assertIsSatisfied();


    }

    @Test
    public void testImportInvalidFileToDataspace() throws Exception {

        String filename = "ruter_gtfs_folder.zip";
        String pathname = "src/test/resources/no/rutebanken/marduk/routes/file/beans/gtfs-folder.zip";

        //populate fake blob repo
        inMemoryBlobStoreRepository.uploadBlob(filename, new FileInputStream(new File(pathname)), false);

        // Mock initial call to Chouette to import job
        context.getRouteDefinition("chouette-send-import-job").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/importer/gtfs")
                        .skipSendToOriginalEndpoint().to("mock:chouetteCreateImport");
            }
        });

        // Mock job polling route - AFTER header validatio (to ensure that we send correct headers in test as well
        context.getRouteDefinition("chouette-validate-job-status-parameters").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("direct:checkJobStatus").skipSendToOriginalEndpoint()
                        .to("mock:pollJobStatus");
            }
        });

        // Mock update status calls
        context.getRouteDefinition("chouette-process-import-status").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint()
                        .to("mock:updateStatus");
                interceptSendToEndpoint("direct:checkScheduledJobsBeforeTriggeringNextAction").skipSendToOriginalEndpoint()
                        .to("mock:checkScheduledJobsBeforeTriggeringNextAction");
            }
        });

        // we must manually start when we are done with all the advice with
        context.start();

        // 1 initial import call
        chouetteCreateImport.expectedMessageCount(1);
        chouetteCreateImport.returnReplyHeader("Location", new SimpleExpression(
                                                                                       chouetteUrl.replace("http4://", "http://") + "/chouette_iev/referentials/rut/scheduled_jobs/1"));


        pollJobStatus.expectedMessageCount(1);

        updateStatus.expectedMessageCount(1);
        checkScheduledJobsBeforeTriggeringNextAction.expectedMessageCount(1);


        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Constants.PROVIDER_ID, "2");
        headers.put(Constants.FILE_NAME, filename);
        headers.put(Constants.CORRELATION_ID, "corr_id");
        headers.put(Constants.FILE_HANDLE, filename);

        assertTrue("Testing invalid file, but file is not invalid.", ZipFileUtils.zipFileContainsSingleFolder(IOUtils.toByteArray(inMemoryBlobStoreRepository.getBlob(filename))));
        importTemplate.sendBodyAndHeaders(null, headers);

        chouetteCreateImport.assertIsSatisfied();
        pollJobStatus.assertIsSatisfied();

        Exchange exchange = pollJobStatus.getReceivedExchanges().get(0);
        exchange.getIn().setHeader("action_report_result", "OK");
        exchange.getIn().setHeader("validation_report_result", "OK");
        processImportResultTemplate.send(exchange);

        checkScheduledJobsBeforeTriggeringNextAction.assertIsSatisfied();
        updateStatus.assertIsSatisfied();

        assertFalse("Invalid file has not been replaced during import.", ZipFileUtils.zipFileContainsSingleFolder(IOUtils.toByteArray(inMemoryBlobStoreRepository.getBlob(filename))));
    }


    @Test
    public void testJobListResponseTerminated() throws Exception {
        testJobListResponse("/no/rutebanken/marduk/chouette/getJobListResponseAllTerminated.json", true);
    }

    @Test
    public void testJobListResponseScheduled() throws Exception {
        testJobListResponse("/no/rutebanken/marduk/chouette/getJobListResponseScheduled.json", false);
    }

    public void testJobListResponse(String jobListResponseClasspathReference, boolean expectExport) throws Exception {

        context.getRouteDefinition("chouette-process-job-list-after-import").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint(chouetteUrl + "/*")
                        .skipSendToOriginalEndpoint()
                        .to("mock:chouetteGetJobsForProvider");
                interceptSendToEndpoint("activemq:queue:ChouetteValidationQueue")
                        .skipSendToOriginalEndpoint()
                        .to("mock:chouetteValidationQueue");
            }
        });

        context.start();

        // 1 call to list other import jobs in referential
        chouetteGetJobs.expectedMessageCount(1);
        chouetteGetJobs.returnReplyBody(new Expression() {

            @SuppressWarnings("unchecked")
            @Override
            public <T> T evaluate(Exchange ex, Class<T> arg1) {
                try {
                    return (T) IOUtils.toString(getClass().getResourceAsStream(jobListResponseClasspathReference));
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    return null;
                }
            }
        });

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Constants.PROVIDER_ID, "2");
        headers.put(Constants.CHOUETTE_REFERENTIAL, "rut");
        headers.put(Constants.ENABLE_VALIDATION, true);

        triggerJobListTemplate.sendBodyAndHeaders(null, headers);

        chouetteGetJobs.assertIsSatisfied();

        if (expectExport) {
            chouetteValidationQueue.expectedMessageCount(1);
        }
        chouetteValidationQueue.assertIsSatisfied();

    }

}