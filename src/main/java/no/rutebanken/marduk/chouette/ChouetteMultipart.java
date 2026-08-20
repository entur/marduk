package no.rutebanken.marduk.chouette;

import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The multipart bodies Chouette's job endpoints expect.
 *
 * <p>Lifted out of {@code AbstractChouetteRouteBuilder} unchanged, so the bytes on the wire are the same
 * ones Chouette has always received. That was the reason for building {@link ChouetteClient} on Apache
 * HttpClient5: this builder produces the entity, and re-encoding it through a different client's multipart
 * writer would be a behaviour change nothing needs.
 */
public final class ChouetteMultipart {

    private static final String PARAMETERS_PART = "parameters";
    private static final String PARAMETERS_FILENAME = "parameters.json";

    private ChouetteMultipart() {
    }

    /** Just the job parameters, for a validation, export or transfer. */
    public static HttpEntity parameters(String jsonPart) {
        return MultipartEntityBuilder.create()
                .addBinaryBody(PARAMETERS_PART, requireJson(jsonPart), ContentType.DEFAULT_BINARY, PARAMETERS_FILENAME)
                .build();
    }

    /** The job parameters plus the dataset, for an import. */
    public static HttpEntity parametersAndFeed(String jsonPart, String fileName, InputStream feed) {
        if (!StringUtils.hasText(fileName)) {
            throw new IllegalArgumentException("No file handle");
        }
        byte[] parameters = requireJson(jsonPart);
        if (feed == null) {
            throw new IllegalArgumentException("No data");
        }
        return MultipartEntityBuilder.create()
                .addBinaryBody(PARAMETERS_PART, parameters, ContentType.DEFAULT_BINARY, PARAMETERS_FILENAME)
                .addBinaryBody("feed", feed, ContentType.DEFAULT_BINARY, fileName)
                .build();
    }

    private static byte[] requireJson(String jsonPart) {
        if (!StringUtils.hasText(jsonPart)) {
            throw new IllegalArgumentException("No json data");
        }
        return jsonPart.getBytes(StandardCharsets.UTF_8);
    }
}
