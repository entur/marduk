package no.rutebanken.marduk.netex;

import jakarta.annotation.PreDestroy;
import no.rutebanken.marduk.otp.Otp2MergedNetexExport;
import no.rutebanken.marduk.pipeline.MardukMdc;
import no.rutebanken.marduk.pipeline.MardukMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The manual backfill of the legacy and new DatedServiceJourney export folders, started from the admin API.
 *
 * <p>Fire and forget: the downgrade of a large dataset takes longer than the HTTP request timeout, so the
 * endpoint answers as soon as the work is queued. The work runs one backfill at a time, on a thread of its own
 * that carries the request's MDC, which is how the {@code wireTap} it replaces behaved apart from the ordering.
 */
@Component
public class NetexDsjBackfill {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetexDsjBackfill.class);

    private final NetexDsjExport netexDsjExport;
    private final Otp2MergedNetexExport mergedNetexExport;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("netex-dsj-backfill-", 0).daemon().factory());

    public NetexDsjBackfill(NetexDsjExport netexDsjExport, Otp2MergedNetexExport mergedNetexExport) {
        this.netexDsjExport = netexDsjExport;
        this.mergedNetexExport = mergedNetexExport;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /** Distributes the current export of one provider, named by the message's referential. */
    public void startDistributingProvider(MardukMessage message) {
        executor.execute(() -> MardukMdc.with(message, () -> run("provider", () -> distributeProvider(message))));
    }

    /** Distributes the current export of every provider, then regenerates the Norway aggregated exports. */
    public void startDistributingAll(MardukMessage message) {
        executor.execute(() -> MardukMdc.with(message, () -> run("all providers", () -> distributeAll(message))));
    }

    void distributeProvider(MardukMessage message) {
        netexDsjExport.distributeExport(message);
    }

    void distributeAll(MardukMessage message) {
        netexDsjExport.distributeAllExports(message, mergedNetexExport.publishedProviders());
        message.setBody("");
        mergedNetexExport.export(message);
    }

    private static void run(String what, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            LOGGER.error("Distributing the NeTEx export of {} to the DatedServiceJourney export folders failed", what, e);
        }
    }
}
