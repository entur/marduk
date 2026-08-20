package no.rutebanken.marduk.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import no.rutebanken.marduk.otp.Otp2BaseGraphBuild;
import no.rutebanken.marduk.otp.Otp2NetexGraphBuild;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts that {@code spring.task.scheduling.pool.size} actually reaches the scheduler {@code @Scheduled}
 * will use.
 *
 * <p>The property is easy to set and easy to have no effect: Boot's auto-configuration backs off when
 * another {@code TaskScheduler} bean exists, and spring-cloud-gcp registers several. antu and damu both
 * shipped with it silently ignored.
 *
 * <p>The pool size here is deliberately a <b>non-default</b> value. antu's first attempt at this test
 * compared the property to the pool size in a context where the property was unset, so both sides were
 * Boot's default of 1 and the assertion was {@code 1 == 1} - it stayed green even when the whole bean was
 * replaced with a bare {@code new ThreadPoolTaskScheduler()}.
 */
class SchedulingConfigTest {

    private static final int NON_DEFAULT_POOL_SIZE = 5;

    /** Street and transit, production and candidate. */
    private static final int GRAPH_BUILD_TICKS = 4;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
            .withUserConfiguration(SchedulingConfig.class)
            .withPropertyValues("spring.task.scheduling.pool.size=" + NON_DEFAULT_POOL_SIZE);

    @Test
    void theConfiguredPoolSizeReachesTheScheduler() {
        contextRunner.run(context -> assertThat(context.getBean("taskScheduler", ThreadPoolTaskScheduler.class)
                .getScheduledThreadPoolExecutor()
                .getCorePoolSize())
                .isEqualTo(NON_DEFAULT_POOL_SIZE));
    }

    @Test
    void theSchedulerIsNamedTaskSchedulerSoScheduledResolvesIt() {
        // ScheduledAnnotationBeanPostProcessor looks the scheduler up by this name before falling back to a
        // unique TaskScheduler by type, and by type is ambiguous once spring-cloud-gcp has registered its own.
        contextRunner.run(context -> assertThat(context).hasBean("taskScheduler"));
    }

    @Test
    void theExplicitBeansWinOverBootSAutoConfiguration() {
        contextRunner.run(context -> assertThat(context.getBeansOfType(TaskScheduler.class))
                .containsOnlyKeys("taskScheduler", "graphBuildScheduler"));
    }

    @Test
    void theGraphBuildsHaveAPoolOfTheirOwn() {
        // A graph build waits on its Kubernetes job for up to otp.graph.build.remote.kubernetes.timeout -
        // 12000 s in the ConfigMap - on the scheduler thread that started it. Four of those on the shared
        // pool of eight and every other @Scheduled method in marduk stops firing, silently.
        contextRunner.run(context -> assertThat(
                context.getBean("graphBuildScheduler", ThreadPoolTaskScheduler.class)
                        .getScheduledThreadPoolExecutor()
                        .getCorePoolSize())
                .isGreaterThanOrEqualTo(GRAPH_BUILD_TICKS));
    }

    @Test
    void everyGraphBuildScheduleNamesTheGraphBuildScheduler() {
        // The whole point of the separate pool is lost if one of the four ticks forgets to ask for it, and
        // nothing at runtime would say so.
        List<Method> ticks = Stream.of(Otp2BaseGraphBuild.class, Otp2NetexGraphBuild.class)
                .flatMap(type -> Stream.of(type.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(Scheduled.class))
                .toList();

        assertThat(ticks).hasSize(GRAPH_BUILD_TICKS);
        assertThat(ticks).allSatisfy(tick -> assertThat(tick.getAnnotation(Scheduled.class).scheduler())
                .withFailMessage("%s does not run on the graph build scheduler", tick.getName())
                .isEqualTo("graphBuildScheduler"));
    }

    @Test
    void aFailingScheduledJobIsLoggedAtError() throws Exception {
        // The antu incident this guards: a scheduled sweeper threw every five minutes for hours and the
        // failure went only to Spring's default handler, under a logger and level the alerting did not watch.
        // A scheduled job that throws is silent in a way a failing consumer is not - nothing nacks, nothing
        // retries, nothing reports a status.
        Logger logger = (Logger) LoggerFactory.getLogger(SchedulingConfig.class);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        logger.addAppender(captured);
        try {
            ThreadPoolTaskScheduler scheduler = new SchedulingConfig().taskScheduler(1);
            scheduler.afterPropertiesSet();
            try {
                CountDownLatch ran = new CountDownLatch(1);
                scheduler.schedule(() -> {
                    ran.countDown();
                    throw new IllegalStateException("the sweeper is broken");
                }, Instant.now());
                assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
                // The handler runs after the task, so give it a moment to record.
                for (int i = 0; i < 50 && captured.list.isEmpty(); i++) {
                    Thread.sleep(20);
                }
            } finally {
                scheduler.shutdown();
            }
        } finally {
            logger.detachAppender(captured);
        }

        assertThat(captured.list)
                .withFailMessage("nothing was logged for a failing scheduled job")
                .isNotEmpty();
        assertThat(captured.list.getFirst().getLevel()).isEqualTo(Level.ERROR);
        assertThat(captured.list.getFirst().getThrowableProxy().getMessage()).isEqualTo("the sweeper is broken");
    }

    @Test
    void thePoolIsBigEnoughForTheScheduledJobsByDefault() {
        // Quartz ran these on a pool of ten, so they could overlap. On a single-threaded executor a slow OSM
        // download would delay the nightly validation trigger with nothing saying so.
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                .withUserConfiguration(SchedulingConfig.class)
                .run(context -> assertThat(context.getBean("taskScheduler", ThreadPoolTaskScheduler.class)
                        .getScheduledThreadPoolExecutor()
                        .getCorePoolSize())
                        .isGreaterThanOrEqualTo(6));
    }
}
