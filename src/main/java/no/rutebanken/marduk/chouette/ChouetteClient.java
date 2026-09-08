package no.rutebanken.marduk.chouette;

import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.pipeline.RetryPolicy;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * HTTP access to Chouette, replacing the {@code http:} producers the Camel routes used.
 *
 * <p>Built on Apache HttpClient5 rather than {@code RestClient} for one reason: the multipart requests are
 * assembled with {@code MultipartEntityBuilder}, and reusing that code unchanged means the bytes Chouette
 * receives are byte-for-byte what it received before. A {@code RestClient} port would have rebuilt the
 * multipart body through a different encoder, which is a behaviour change nothing here needs.
 *
 * <p>Two Camel behaviours are reproduced deliberately:
 *
 * <ul>
 *   <li><b>Non-2xx throws.</b> Camel's http component defaults to {@code throwExceptionOnFailure=true}, so
 *       a 4xx or 5xx failed the exchange rather than returning a body. The callers rely on that to report a
 *       failed job.
 *   <li><b>Redirects are followed</b>, matching {@code camel.component.http.follow-redirects=true} in the
 *       ConfigMap.
 * </ul>
 *
 * <p>No credentials are sent. Chouette is reached over the internal network, and the Camel routes called
 * {@code removeHttpHeaders} first precisely to strip the inbound {@code Authorization} header before
 * calling it.
 *
 * <p>The pool and the timeouts are Camel's {@code HttpComponent} defaults, read off the 4.21.0 sources:
 * {@code maxTotalConnections=200}, {@code connectionsPerRoute=20}, and 3 minutes for
 * {@code connectTimeout}, {@code soTimeout} and {@code connectionRequestTimeout}. HttpClient5's own
 * defaults are 25 connections, 5 per route and no timeout at all, so a hung Chouette held a consumer
 * thread for ever and the sixth concurrent call to the same host queued behind the first five.
 */
@Component
public class ChouetteClient implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChouetteClient.class);

    /** Camel's HttpComponent defaults. */
    private static final Timeout TIMEOUT = Timeout.ofMinutes(3);
    private static final int MAX_CONNECTIONS = 200;
    private static final int MAX_CONNECTIONS_PER_ROUTE = 20;

    private final String baseUrl;
    private final CloseableHttpClient httpClient;
    private final RetryPolicy retryPolicy;

    public ChouetteClient(@Value("${chouette.url}") String chouetteUrl, RetryPolicy retryPolicy) {
        this.baseUrl = normalise(chouetteUrl);
        this.retryPolicy = retryPolicy;
        this.httpClient = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(MAX_CONNECTIONS)
                        .setMaxConnPerRoute(MAX_CONNECTIONS_PER_ROUTE)
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(TIMEOUT)
                                .setSocketTimeout(TIMEOUT)
                                .build())
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(TIMEOUT)
                        .setResponseTimeout(TIMEOUT)
                        .build())
                .build();
    }

    /**
     * The configured base URL, with Camel's dynamic-endpoint spelling repaired.
     *
     * <p>{@code chouette.url} is a normal URL in every deployed environment. Only the test configuration
     * uses the {@code http:host:port} form, which existed because Camel's endpoint interception could not
     * cope with {@code //} in a dynamic {@code toD}. {@code ChouettePollJobStatusRoute} carried the same
     * repair inline; it is kept here so the test configuration does not have to change in the same commit
     * as the code.
     */
    static String normalise(String chouetteUrl) {
        return chouetteUrl.contains("://") ? chouetteUrl : chouetteUrl.replaceFirst(":", "://");
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** The response body as text. */
    public String getString(String url) {
        return execute(new HttpGet(absolute(url)), response -> asString(response.getEntity()));
    }

    /**
     * The response body as bytes.
     *
     * <p>For small payloads only - a job status, an action report. A NeTEx export is hundreds of megabytes
     * and must go through {@link #downloadTo(String, Path)} instead: the Camel routes that fetched exports
     * had {@code .streamCaching()} and spooled them to disk, and holding one in a {@code byte[]} would put
     * it on the heap of a pod whose limit is smaller than the file.
     */
    public byte[] getBytes(String url) {
        return execute(new HttpGet(absolute(url)), response -> asBytes(response.getEntity()));
    }

    /** Streams the response body to {@code target}, for an export too large to hold in memory. */
    public void downloadTo(String url, Path target) {
        execute(new HttpGet(absolute(url)), response -> {
            try (InputStream body = response.getEntity().getContent()) {
                Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return null;
        });
    }

    public void post(String url) {
        execute(new HttpPost(absolute(url)), response -> null);
    }

    public void delete(String url) {
        execute(new HttpDelete(absolute(url)), response -> null);
    }

    /**
     * Submits a job and returns the {@code Location} of the created job, which is what the callers poll.
     *
     * @throws MardukException if Chouette accepted the request without saying where the job is
     */
    public String postMultipart(String url, HttpEntity multipart) {
        HttpPost request = new HttpPost(absolute(url));
        request.setEntity(multipart);
        // The only call not retried: the entity reads a blob store stream that cannot be replayed, so a
        // second attempt would post a truncated body, and a submission that reached Chouette before the
        // failure has already created a job.
        return executeOnce(request, response -> {
            Header location = response.getFirstHeader("Location");
            if (location == null) {
                throw new MardukException("Chouette accepted " + url + " without returning a Location header");
            }
            return location.getValue();
        });
    }

    private String absolute(String url) {
        return url.startsWith("http") ? url : baseUrl + url;
    }

    /**
     * Runs the request under the redelivery policy every route inherited from {@code BaseRouteBuilder},
     * which is what used to absorb a Chouette restart or a dropped connection. The retry sits here rather
     * than around the message so a call that already succeeded is not repeated.
     */
    private <T> T execute(HttpUriRequestBase request, ResponseHandler<T> handler) {
        return retryPolicy.call(request.getMethod() + " " + request.getRequestUri(),
                () -> executeOnce(request, handler));
    }

    private <T> T executeOnce(HttpUriRequestBase request, ResponseHandler<T> handler) {
        LOGGER.debug("Calling Chouette: {} {}", request.getMethod(), request.getRequestUri());
        try {
            return httpClient.execute(request, response -> {
                int status = response.getCode();
                if (status < 200 || status >= 300) {
                    // Camel failed the exchange on a non-2xx; the body usually says why, so it goes in the
                    // message rather than being discarded.
                    throw new MardukException("Chouette returned " + status + " for "
                            + request.getMethod() + " " + request.getRequestUri() + ": " + asString(response.getEntity()));
                }
                return handler.handle(response);
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to call Chouette: " + request.getRequestUri(), e);
        }
    }

    private static String asString(HttpEntity entity) {
        if (entity == null) {
            return "";
        }
        try {
            return EntityUtils.toString(entity);
        } catch (IOException | org.apache.hc.core5.http.ParseException e) {
            throw new MardukException("Could not read the response body from Chouette", e);
        }
    }

    private static byte[] asBytes(HttpEntity entity) {
        if (entity == null) {
            return new byte[0];
        }
        try {
            return EntityUtils.toByteArray(entity);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the response body from Chouette", e);
        }
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }

    @FunctionalInterface
    private interface ResponseHandler<T> {
        T handle(ClassicHttpResponse response) throws IOException;
    }
}
