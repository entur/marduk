package no.rutebanken.marduk.chouette;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import no.rutebanken.marduk.rest.ProviderAndJobs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static no.rutebanken.marduk.pipeline.RetryPolicies.noRetries;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Driven against a real HTTP server, like {@link ChouetteClientTest}: the parts worth pinning are the URL
 * Chouette is asked for and the order the cancellations go out in.
 */
class ChouetteJobsTest {

    private record Received(String method, String path, String query) {
    }

    private HttpServer server;
    private ChouetteClient client;
    private ChouetteJobs jobs;
    private ProviderRepository providerRepository;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private volatile String responseBody = "[]";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();
        client = new ChouetteClient("http://127.0.0.1:" + server.getAddress().getPort(), noRetries());

        providerRepository = mock(ProviderRepository.class);
        when(providerRepository.getProvider(2L)).thenReturn(provider(2L, "rut", 4L));
        when(providerRepository.getProvider(4L)).thenReturn(provider(4L, "rb_rut", null));
        when(providerRepository.getProviders()).thenReturn(List.of(provider(2L, "rut", 4L), provider(4L, "rb_rut", null)));

        jobs = new ChouetteJobs(client, providerRepository);
    }

    @AfterEach
    void stopServer() throws IOException {
        jobs.stopFanOut();
        client.close();
        server.stop(0);
    }

    private static Provider provider(long id, String referential, Long migrateTo) {
        Provider provider = new Provider();
        provider.setId(id);
        ChouetteInfo info = new ChouetteInfo();
        info.setReferential(referential);
        info.setMigrateDataToProvider(migrateTo);
        provider.setChouetteInfo(info);
        return provider;
    }

    private void respond(HttpExchange exchange) throws IOException {
        try (InputStream body = exchange.getRequestBody()) {
            body.readAllBytes();
        }
        received.add(new Received(exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(), exchange.getRequestURI().getQuery()));
        byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, payload.length);
        try (var out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private static String jobList(int... ids) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < ids.length; i++) {
            json.append(i == 0 ? "" : ",")
                    .append("{\"id\":").append(ids[i]).append(",\"referential\":\"rut\",\"status\":\"STARTED\"}");
        }
        return json.append("]").toString();
    }

    @Test
    void theJobListIsFetchedForTheProvidersReferential() {
        responseBody = jobList(1, 2);

        List<JobResponse> found = jobs.jobsFor(2L, List.of("STARTED"), "importer");

        assertEquals(2, found.size());
        assertEquals("/chouette_iev/referentials/rut/jobs", received.getFirst().path());
        assertTrue(received.getFirst().query().contains("status=STARTED"));
        assertTrue(received.getFirst().query().contains("action=importer"));
    }

    @Test
    void everyRequestedStatusBecomesItsOwnQueryParameter() {
        jobs.jobsFor(2L, List.of("STARTED", "SCHEDULED"), null);

        String query = received.getFirst().query();
        assertTrue(query.contains("status=STARTED") && query.contains("status=SCHEDULED"), query);
    }

    @Test
    void theJobParametersAreLeftOutOfTheResponse() {
        // Chouette otherwise inlines every job's import or export parameters, which nothing here reads.
        jobs.jobsFor(2L, null, null);

        assertTrue(received.getFirst().query().contains("addActionParameters=false"));
    }

    @Test
    void theJobsOfEveryProviderAreGroupedByProvider() {
        responseBody = jobList(1, 2, 3);

        List<ProviderAndJobs> perProvider = jobs.allJobsPerProvider(List.of("STARTED"), null);

        assertEquals("/chouette_iev/referentials/jobs", received.getFirst().path());
        assertEquals(2, perProvider.size());
        assertEquals(3, perProvider.stream().filter(p -> p.getProviderId() == 2L).findFirst().orElseThrow().getNumJobs());
    }

    @Test
    void aJobIsCancelledWithADelete() {
        jobs.cancel(2L, "17");

        assertEquals(new Received("DELETE", "/chouette_iev/referentials/rut/scheduled_jobs/17", null),
                received.getFirst());
    }

    @Test
    void cancellingEverythingStartsWithTheNewestJob() {
        // Oldest first would let the next queued job start behind the one just cancelled.
        responseBody = jobList(1, 3, 2);

        jobs.cancelAllFor(2L);

        List<String> deleted = received.stream().filter(r -> "DELETE".equals(r.method())).map(Received::path).toList();
        assertEquals(List.of("/chouette_iev/referentials/rut/scheduled_jobs/3",
                        "/chouette_iev/referentials/rut/scheduled_jobs/2",
                        "/chouette_iev/referentials/rut/scheduled_jobs/1"),
                deleted);
    }

    @Test
    void cancellingEverythingOnlyLooksAtUnfinishedJobs() {
        jobs.cancelAllFor(2L);

        String query = received.getFirst().query();
        assertTrue(query.contains("status=STARTED") && query.contains("status=SCHEDULED"), query);
    }

    @Test
    void oneJobThatCannotBeCancelledDoesNotStopTheRest() throws IOException {
        // The route nested a second parallel split over the job list, so one refused cancellation cost only
        // that job. A plain loop would abandon every job behind it - the newest ones, which matter most.
        server.stop(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            received.add(new Received(exchange.getRequestMethod(), path, null));
            byte[] payload = jobList(1, 3, 2).getBytes(StandardCharsets.UTF_8);
            if (path.endsWith("/scheduled_jobs/3")) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, payload.length);
            try (var out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
        client.close();
        client = new ChouetteClient("http://127.0.0.1:" + server.getAddress().getPort(), noRetries());
        jobs = new ChouetteJobs(client, providerRepository);

        assertThrows(MardukException.class, () -> jobs.cancelAllFor(2L));

        List<String> deleted = received.stream().filter(r -> "DELETE".equals(r.method())).map(Received::path).toList();
        assertEquals(List.of("/chouette_iev/referentials/rut/scheduled_jobs/3",
                        "/chouette_iev/referentials/rut/scheduled_jobs/2",
                        "/chouette_iev/referentials/rut/scheduled_jobs/1"),
                deleted, "the jobs behind the failing one were abandoned");
    }

    @Test
    void aStopPlaceCleanIsOneCall() {
        jobs.cleanStopPlaces();

        assertEquals(new Received("POST", "/chouette_iev/referentials/clean/stop_areas", null), received.getFirst());
    }

    @Test
    void aDataspaceCleanNamesTheProvidersReferential() {
        jobs.clean(2L);

        assertEquals(new Received("POST", "/chouette_iev/referentials/rut/clean", null), received.getFirst());
    }

    @Test
    void level1CleansOnlyTheProvidersThatMigrateTheirDataOnwards() {
        jobs.cleanAll("level1");

        assertEquals(Set.of("/chouette_iev/referentials/rut/clean"), cleaned());
    }

    @Test
    void level2CleansOnlyTheProvidersThatKeepTheirData() {
        jobs.cleanAll("level2");

        assertEquals(Set.of("/chouette_iev/referentials/rb_rut/clean"), cleaned());
    }

    @Test
    void allCleansEveryDataspace() {
        jobs.cleanAll("all");

        assertEquals(Set.of("/chouette_iev/referentials/rut/clean", "/chouette_iev/referentials/rb_rut/clean"),
                cleaned());
    }

    @Test
    void anUnknownFilterCleansNothing() {
        // The route validated the same three values but turned a typo into a 500; the caller answers 400.
        assertThrows(IllegalArgumentException.class, () -> jobs.cleanAll("level3"));
        assertTrue(received.isEmpty(), "a bad filter must not reach Chouette");
    }

    @Test
    void oneUnreachableDataspaceDoesNotStopTheOthers() throws IOException {
        // parallelProcessing() on the split had the same property: every provider is attempted.
        server.stop(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), null));
            exchange.sendResponseHeaders(exchange.getRequestURI().getPath().contains("rb_rut") ? 500 : 200, -1);
            exchange.close();
        });
        server.start();
        client.close();
        client = new ChouetteClient("http://127.0.0.1:" + server.getAddress().getPort(), noRetries());
        jobs = new ChouetteJobs(client, providerRepository);

        assertThrows(MardukException.class, () -> jobs.cleanAll("all"));
        assertEquals(2, received.stream().filter(r -> r.path().endsWith("/clean")).count());
    }

    private Set<String> cleaned() {
        return received.stream().filter(r -> "POST".equals(r.method())).map(Received::path)
                .collect(java.util.stream.Collectors.toSet());
    }
}
