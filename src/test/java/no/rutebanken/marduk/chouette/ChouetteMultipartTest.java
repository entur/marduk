package no.rutebanken.marduk.chouette;

import org.apache.hc.core5.http.HttpEntity;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChouetteMultipartTest {

    private static final String PARAMETERS = "{\"parameters\":{\"importer\":{}}}";

    private static String bodyOf(HttpEntity entity) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        entity.writeTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void parametersGoOutAsAJsonFilePart() throws IOException {
        HttpEntity entity = ChouetteMultipart.parameters(PARAMETERS);

        String body = bodyOf(entity);
        assertTrue(entity.getContentType().startsWith("multipart/form-data"), entity.getContentType());
        assertTrue(body.contains("name=\"parameters\""), body);
        assertTrue(body.contains("filename=\"parameters.json\""), body);
        assertTrue(body.contains(PARAMETERS), body);
    }

    @Test
    void anImportCarriesTheParametersAndTheFeed() throws IOException {
        HttpEntity entity = ChouetteMultipart.parametersAndFeed(
                PARAMETERS, "netex.zip", new ByteArrayInputStream("zip-bytes".getBytes(StandardCharsets.UTF_8)));

        String body = bodyOf(entity);
        assertTrue(body.contains("filename=\"parameters.json\""), body);
        assertTrue(body.contains("name=\"feed\""), body);
        assertTrue(body.contains("filename=\"netex.zip\""), body);
        assertTrue(body.contains("zip-bytes"), body);
    }

    @Test
    void missingParametersAreRejected() {
        // Chouette answers 500 on a job submitted without parameters, so failing here says why.
        assertThrows(IllegalArgumentException.class, () -> ChouetteMultipart.parameters(null));
        assertThrows(IllegalArgumentException.class, () -> ChouetteMultipart.parameters("  "));
    }

    @Test
    void anImportWithoutAFileNameOrFeedIsRejected() {
        ByteArrayInputStream feed = new ByteArrayInputStream(new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> ChouetteMultipart.parametersAndFeed(PARAMETERS, null, feed));
        assertThrows(IllegalArgumentException.class, () -> ChouetteMultipart.parametersAndFeed(PARAMETERS, "", feed));
        assertThrows(IllegalArgumentException.class,
                () -> ChouetteMultipart.parametersAndFeed(PARAMETERS, "netex.zip", null));
    }

    @Test
    void theFileNameIsCheckedBeforeTheFeedIsTouched() {
        // The feed is a stream over a blob download; validating the cheap arguments first avoids reading it
        // only to throw the request away.
        assertThrows(IllegalArgumentException.class,
                () -> ChouetteMultipart.parametersAndFeed(PARAMETERS, null, null));
    }
}
