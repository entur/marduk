/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Asks the production OTP instances which serialization version they run, through their Transmodel GraphQL API.
 * <p>
 * The OTP instances load the stable copy of the current graph of their own serialization version: this is the
 * serialization version used in production. The root pointer file is only written by production builds and
 * production rollouts: after an OTP release that changes the serialization version, it names a graph of the previous
 * serialization version until the first production build of the new one, and after a downgrade a graph of the newer
 * one.
 */
@Component
public class Otp2ProductionVersionClient {

    static final String CLIENT_NAME = "entur-marduk";
    static final String QUERY = "{\"query\":\"{ serverInfo { otpSerializationVersionId } }\"}";

    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final int MAX_LOGGED_ANSWER_LENGTH = 300;

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getSharedObjectMapper();
    private static final Logger LOGGER = LoggerFactory.getLogger(Otp2ProductionVersionClient.class);

    private final HttpClient httpClient;
    private final HttpRequest request;

    /**
     * @param graphqlUrl the Transmodel GraphQL endpoint of the production OTP instances. If empty, the client is turned
     *                   off: it never answers, and the serialization version used in production is read from the root
     *                   pointer file.
     */
    @Autowired
    public Otp2ProductionVersionClient(@Value("${otp2.production.graphql.url:}") String graphqlUrl) {
        this(graphqlUrl, TIMEOUT);
    }

    Otp2ProductionVersionClient(String graphqlUrl, Duration timeout) {
        if (graphqlUrl == null || graphqlUrl.isBlank()) {
            LOGGER.info("otp2.production.graphql.url is not set: the serialization version used in production is read from the root pointer file");
            this.httpClient = null;
            this.request = null;
        } else {
            this.httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(timeout)
                    .build();
            this.request = HttpRequest.newBuilder(URI.create(graphqlUrl.trim()))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("ET-Client-Name", CLIENT_NAME)
                    .POST(HttpRequest.BodyPublishers.ofString(QUERY))
                    .build();
        }
    }

    /**
     * The serialization version of the production OTP instances. While they are redeployed with another OTP version,
     * the answer depends on the instance that serves the request.
     *
     * @return the serialization version, or empty if the client is turned off, or if the OTP instances do not answer in
     * time with a serialization version
     */
    public Optional<String> serializationId() {
        if (request == null) {
            return Optional.empty();
        }
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOGGER.warn("The production OTP instances answered HTTP {} to the serialization version query at {}: {}",
                        response.statusCode(), request.uri(), abbreviated(response.body()));
                return Optional.empty();
            }
            JsonNode serializationId = OBJECT_MAPPER.readTree(response.body()).path("data").path("serverInfo").path("otpSerializationVersionId");
            if (!serializationId.isTextual() || serializationId.asText().isBlank()) {
                LOGGER.warn("No serialization version in the answer of the production OTP instances at {}: {}",
                        request.uri(), abbreviated(response.body()));
                return Optional.empty();
            }
            return Optional.of(serializationId.asText());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while asking the production OTP instances at {} for their serialization version", request.uri());
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to ask the production OTP instances at {} for their serialization version: {}", request.uri(), e.toString());
            return Optional.empty();
        }
    }

    private static String abbreviated(String answer) {
        if (answer == null || answer.length() <= MAX_LOGGED_ANSWER_LENGTH) {
            return answer;
        }
        return answer.substring(0, MAX_LOGGED_ANSWER_LENGTH) + "…";
    }
}
