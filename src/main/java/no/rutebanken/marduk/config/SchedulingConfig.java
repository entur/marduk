package no.rutebanken.marduk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * The {@code taskScheduler} bean Spring Boot would normally contribute, declared explicitly because here
 * it does not.
 *
 * <p>{@code TaskSchedulingAutoConfiguration} is {@code @ConditionalOnMissingBean(TaskScheduler.class)} and
 * spring-cloud-gcp registers several of its own - the publisher pool, the global subscriber pool, and one
 * per subscription. So Boot backs off, nothing is named {@code taskScheduler}, and {@code @Scheduled}
 * quietly builds a single-threaded executor of its own while {@code spring.task.scheduling.pool.size} stops
 * meaning anything. The only signal is one INFO line from {@code TaskSchedulerRouter} naming the competing
 * beans, once per boot, which is how it survived review in antu and damu.
 *
 * <p>This matters more here than it did there. Quartz ran marduk's scheduled routes on its own pool of ten,
 * so the nightly validation trigger, the OSM fetch and the Chouette job cleanup could overlap. On a
 * single-threaded executor a slow OSM download would hold up the others, and nothing would say so.
 */
@Configuration
public class SchedulingConfig {

    /**
     * Named {@code taskScheduler} on purpose: {@code ScheduledAnnotationBeanPostProcessor} resolves the
     * scheduler by that name before falling back to a unique {@code TaskScheduler} by type, and by type is
     * ambiguous here.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulingConfig.class);

    @Bean
    ThreadPoolTaskScheduler taskScheduler(
            // Default sized for the scheduled jobs that replace the quartz routes, so one slow job cannot
            // delay another. Asserted against the bean by SchedulingConfigTest.
            @Value("${spring.task.scheduling.pool.size:8}") int poolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("marduk-scheduling-");
        // Scheduled jobs are the leader's periodic work; none of them is worth blocking a shutdown for,
        // and the drain covers the queue-driven work instead.
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setErrorHandler(SchedulingConfig::logScheduledFailure);
        return scheduler;
    }

    /**
     * The scheduler the OTP2 graph builds run on, named by {@code @Scheduled(scheduler = ...)}.
     *
     * <p>They cannot share the pool above. A graph build waits on a Kubernetes job for up to
     * {@code otp.graph.build.remote.kubernetes.timeout} - 12000 s in the ConfigMap - and it waits on the
     * scheduler thread that started it. Four such builds would hold half of a pool of eight for three hours
     * each, and every other {@code @Scheduled} method in marduk would stop firing without a word.
     *
     * <p>Sized for the four graph build ticks - street and transit, production and candidate - so a tick
     * always has a thread even while the other three are building.
     */
    @Bean
    ThreadPoolTaskScheduler graphBuildScheduler(
            @Value("${marduk.graph.build.scheduler.pool.size:4}") int poolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("marduk-graph-build-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setErrorHandler(SchedulingConfig::logScheduledFailure);
        return scheduler;
    }

    /**
     * Logs a failing scheduled job at ERROR, with the job named.
     *
     * <p>This exists because of a specific incident in antu. Its stalled-work sweeper - the only thing that
     * concluded a stuck job - threw every five minutes for hours, and because nothing set an error handler
     * the failure went only to Spring's default one, which logs under
     * {@code TaskUtils$LoggingErrorHandler} at a level and message the alerting did not watch. The safety net
     * was down and the service looked healthy.
     *
     * <p>A scheduled job that throws is invisible in a way a failing consumer is not: nothing nacks, nothing
     * retries, nothing reports a status. Whatever the job was protecting simply stops happening. So the log
     * line has to be loud, and it has to say which job.
     */
    private static void logScheduledFailure(Throwable failure) {
        LOGGER.error("Scheduled job failed; whatever it protects is not happening until the next firing",
                failure);
    }
}
