package no.rutebanken.marduk.chouette;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PreDestroy;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import no.rutebanken.marduk.routes.chouette.mapping.ProviderAndJobsMapper;
import no.rutebanken.marduk.rest.ProviderAndJobs;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The Chouette job operations the admin API exposes: listing jobs, cancelling them, and cleaning a
 * dataspace.
 *
 * <p>Replaces the {@code direct:chouetteGetJobs*}, {@code direct:chouetteCancelJob*} and
 * {@code direct:chouetteClean*} routes. Read-only or operator-triggered, and synchronous: the caller is an
 * HTTP request waiting for the answer.
 */
@Component
public class ChouetteJobs {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChouetteJobs.class);

    private static final TypeReference<List<JobResponse>> JOB_LIST = new TypeReference<>() {
    };

    /** The statuses "cancel everything" means: a job that has not finished yet. */
    private static final List<String> UNFINISHED = List.of("STARTED", "SCHEDULED");

    /** Newest first, so cancelling a queue does not let an older job start behind the one just cancelled. */
    private static final Comparator<JobResponse> NEWEST_FIRST = Comparator.comparing(JobResponse::getId).reversed();

    private final ChouetteClient chouetteClient;
    private final ProviderRepository providerRepository;

    /**
     * Bulk operations fan out over every provider. Bounded at the same 20 as the Camel pool it replaces -
     * the limit is there to keep an operator's one click from opening a hundred connections to Chouette.
     */
    private final ExecutorService providerFanOut = Executors.newFixedThreadPool(
            20, Thread.ofPlatform().name("chouette-all-providers-", 0).factory());

    public ChouetteJobs(ChouetteClient chouetteClient, ProviderRepository providerRepository) {
        this.chouetteClient = chouetteClient;
        this.providerRepository = providerRepository;
    }

    public List<JobResponse> jobsFor(Long providerId, List<String> statuses, String action) {
        String referential = referentialOf(providerId);
        LOGGER.debug("Fetching jobs for provider id '{}'", providerId);
        return jobs("/chouette_iev/referentials/" + referential + "/jobs", statuses, action);
    }

    public List<ProviderAndJobs> allJobsPerProvider(List<String> statuses, String action) {
        LOGGER.debug("Fetching jobs for all providers");
        List<JobResponse> jobs = jobs("/chouette_iev/referentials/jobs", statuses, action);
        return new ProviderAndJobsMapper()
                .mapJobResponsesToProviderAndJobs(jobs.toArray(new JobResponse[0]), providerRepository.getProviders());
    }

    /**
     * Whether the dataspace still has an import waiting to run.
     *
     * <p>The query is {@code timetableAction=importer}, which is what the route asked for. Chouette's
     * documented parameter is {@code action}, so this most likely filters nothing and the answer covers every
     * unfinished job in the dataspace - preserved rather than corrected, because narrowing it would start
     * triggering validation in cases that are skipped today.
     */
    public boolean hasQueuedImports(String referential) {
        String url = "/chouette_iev/referentials/" + referential
                + "/jobs?timetableAction=importer&status=SCHEDULED&status=STARTED";
        return parse(chouetteClient.getString(url)).stream()
                .anyMatch(job -> job.getStatus() == no.rutebanken.marduk.routes.chouette.json.Status.SCHEDULED);
    }

    public void cancel(Long providerId, String jobId) {
        chouetteClient.delete("/chouette_iev/referentials/" + referentialOf(providerId)
                + "/scheduled_jobs/" + jobId);
    }

    /**
     * Cancels every unfinished job of one dataspace.
     *
     * <p>A job that cannot be cancelled costs only itself: the route split over the job list with
     * {@code parallelProcessing()} and no {@code stopOnException}, so the remaining jobs were still
     * attempted. The failure is still rethrown, because the split propagated it to the caller.
     */
    public void cancelAllFor(Long providerId) {
        RuntimeException firstFailure = null;
        for (JobResponse job : jobsFor(providerId, UNFINISHED, null).stream().sorted(NEWEST_FIRST).toList()) {
            try {
                cancel(providerId, String.valueOf(job.getId()));
            } catch (RuntimeException e) {
                LOGGER.warn("Could not cancel Chouette job {} for provider {}", job.getId(), providerId, e);
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    public void cancelAllForAllProviders() {
        forEveryProvider(List.copyOf(providerRepository.getProviders()), provider -> cancelAllFor(provider.getId()));
    }

    public void cleanStopPlaces() {
        LOGGER.info("Starting Chouette stop place clean");
        chouetteClient.post("/chouette_iev/referentials/clean/stop_areas");
    }

    public void clean(Long providerId) {
        LOGGER.info("Starting Chouette dataspace clean for provider {}", providerId);
        chouetteClient.post("/chouette_iev/referentials/" + referentialOf(providerId) + "/clean");
    }

    /**
     * Cleans every dataspace the filter selects.
     *
     * @param filter {@code level1} for the providers that migrate their data onwards, {@code level2} for the
     *               ones that keep it, {@code all} for both
     * @throws IllegalArgumentException on any other filter. The route validated the same three values
     *                                  {@code .validate(header("filter").in(...))} inside the split, and the
     *                                  splitter's default aggregation propagated the failure, so a typo
     *                                  became a 500 after three redeliveries per provider. Raised before the
     *                                  fan-out here, which the caller answers 400 - a deliberate divergence.
     */
    public void cleanAll(String filter) {
        LOGGER.info("Starting Chouette clean of all dataspaces, filter {}", filter);
        forEveryProvider(providerRepository.getProviders().stream().filter(selected(filter)).toList(),
                provider -> clean(provider.getId()));
    }

    private static java.util.function.Predicate<Provider> selected(String filter) {
        return switch (filter) {
            case "all" -> provider -> true;
            case "level1" -> provider -> provider.getChouetteInfo().getMigrateDataToProvider() != null;
            case "level2" -> provider -> provider.getChouetteInfo().getMigrateDataToProvider() == null;
            default -> throw new IllegalArgumentException(
                    "Unknown filter '" + filter + "', expected one of all, level1, level2");
        };
    }

    private List<JobResponse> jobs(String path, List<String> statuses, String action) {
        return parse(chouetteClient.getString(withQuery(path, statuses, action)));
    }

    /**
     * {@code addActionParameters=false} keeps the response small: Chouette otherwise inlines every job's
     * import or export parameters, which nothing here reads.
     */
    private static String withQuery(String path, List<String> statuses, String action) {
        try {
            URIBuilder query = new URIBuilder(path);
            if (action != null) {
                query.addParameter("action", action);
            }
            if (statuses != null) {
                statuses.forEach(status -> query.addParameter("status", status));
            }
            query.addParameter("addActionParameters", Boolean.FALSE.toString());
            return query.toString();
        } catch (URISyntaxException e) {
            throw new MardukException("Could not build the Chouette jobs URL for " + path, e);
        }
    }

    private static List<JobResponse> parse(String json) {
        try {
            return ObjectMapperFactory.getSharedObjectMapper().readValue(json, JOB_LIST);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the job list Chouette returned", e);
        }
    }

    private String referentialOf(Long providerId) {
        return providerRepository.getProvider(providerId).getChouetteInfo().getReferential();
    }

    /**
     * Runs {@code work} for every provider and waits for all of them.
     *
     * <p>The first failure is rethrown once every task has finished, so one unreachable dataspace does not
     * stop the others - which is what {@code parallelProcessing()} on the split did.
     */
    private void forEveryProvider(List<Provider> providers, java.util.function.Consumer<Provider> work) {
        List<Future<?>> running = new java.util.ArrayList<>();
        providers.forEach(provider -> running.add(providerFanOut.submit(() -> work.accept(provider))));
        RuntimeException firstFailure = null;
        for (Future<?> task : running) {
            try {
                task.get();
            } catch (ExecutionException e) {
                LOGGER.warn("A provider failed during a bulk Chouette operation", e.getCause());
                if (firstFailure == null) {
                    firstFailure = new MardukException("A bulk Chouette operation failed", e.getCause());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MardukException("Interrupted during a bulk Chouette operation", e);
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    @PreDestroy
    void stopFanOut() {
        providerFanOut.shutdownNow();
    }
}
