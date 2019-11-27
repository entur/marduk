
package no.rutebanken.marduk.routes.otp.netex;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.CronJob;
import io.fabric8.kubernetes.api.model.batch.CronJobBuilder;
import io.fabric8.kubernetes.api.model.batch.CronJobSpec;
import io.fabric8.kubernetes.api.model.batch.Job;
import io.fabric8.kubernetes.api.model.batch.JobBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class RemoteOtpGraphRunner {

    //public static final String NAMESPACE = "dev";
    public static final String NAMESPACE = "default";
    //public static final String JOB_NAME = "graph-builder";
    public static final String JOB_NAME = "hello2";
    public static final String OTP_WORK_DIR_ENV_VAR = "OTP_WORK_DIR";

    private static final Logger logger = LoggerFactory.getLogger(RemoteOtpGraphRunner.class);

    private String otpWorkDir;
    private String timestamp;

    public RemoteOtpGraphRunner(String otpWorkDir, String timestamp) {
        this.otpWorkDir = otpWorkDir;
        this.timestamp=timestamp;
    }

    public void runRemoteOtpGraphBuild() {
        try (final KubernetesClient client = new DefaultKubernetesClient()) {

            CronJobSpec specTemplate = getCronJobSpecTemplate(client);
            String jobName = JOB_NAME + '-' + timestamp;

            logger.info("Creating cronjob with name {} ", jobName);
            CronJob cronJob = buildCronJobfromSpecTemplate(specTemplate, jobName, otpWorkDir);

            cronJob = client.batch().cronjobs().inNamespace(NAMESPACE).create(cronJob);
            logger.info("Successfully created cronjob with name {}", cronJob.getMetadata().getName());

            final CountDownLatch watchLatch = new CountDownLatch(1);
            try (Watch watch = client.pods().inNamespace(NAMESPACE).withLabel("job-name").watch(new Watcher<Pod>() {
                @Override
                public void eventReceived(Action action, Pod aPod) {
                    logger.info(aPod.getMetadata().getName(), aPod.getStatus().getPhase());
                    if(aPod.getStatus().getPhase().equals("Succeeded")) {
                        logger.info("Logs -> ", client.pods().inNamespace(NAMESPACE).withName(aPod.getMetadata().getName()).getLog());
                        watchLatch.countDown();
                    }
                }

                @Override
                public void onClose(KubernetesClientException e) {
                    // Ignore
                }
            })) {
                watchLatch.await(2, TimeUnit.MINUTES);
            } catch (KubernetesClientException | InterruptedException e) {
                logger.info("Could not watch pod", e);
            }



        }
    }

    protected CronJobSpec getCronJobSpecTemplate(KubernetesClient client) {
        List<CronJob> matchingJobs = client.batch().cronjobs().inNamespace(NAMESPACE).withLabel("name", JOB_NAME).list().getItems();
        if (matchingJobs.isEmpty()) {
            throw new RuntimeException("Job " + JOB_NAME + " not found");
        }
        if (matchingJobs.size() > 1) {
            throw new RuntimeException("Found multiple jobs matching name=" + JOB_NAME);
        }
        return matchingJobs.get(0).getSpec();
    }


    protected CronJob buildCronJobfromSpecTemplate(CronJobSpec specTemplate, String jobName, String otpWorkDir) {

        CronJob cronJob = new CronJobBuilder().withSpec(specTemplate).
                withNewMetadata()
                .withName(jobName)
                .endMetadata()
                .editOrNewSpec()
                .editJobTemplate()
                .editOrNewSpec()
                .editTemplate()
                .editSpec()
                .editFirstContainer()
                .addNewEnv()
                .withName(OTP_WORK_DIR_ENV_VAR)
                .withValue(otpWorkDir)
                .endEnv()
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .endJobTemplate()
                .endSpec()
                .build();
        return cronJob;

    }
}