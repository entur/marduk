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

package no.rutebanken.marduk.routes.file;

import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.Part;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileUploadContentCacheTest {

    private DefaultCamelContext context;

    // Replicate the production stream caching config: globally disabled, per-route opt-in, disk spool
    // enabled. The route-level opt-in is what enables the context-wide strategy at startup.
    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.setStreamCaching(false);
        context.getStreamCachingStrategy().setSpoolEnabled(true);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:streamCachingOptIn").streamCaching().to("mock:sink");
            }
        });
        context.start();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void spoolsLargePartToDiskAndSupportsRepeatedReads() throws Exception {
        byte[] content = new byte[512 * 1024];
        new Random(42).nextBytes(content);

        StreamCache cache = cachePart(content);

        assertFalse(cache.inMemory(), "content above the spool threshold must not be held on the heap");
        assertArrayEquals(content, readFully(cache));
        cache.reset();
        assertArrayEquals(content, readFully(cache), "cache must be re-readable after reset (digest + upload)");
    }

    @Test
    void keepsSmallPartInMemory() throws Exception {
        byte[] content = "small part".getBytes(StandardCharsets.UTF_8);

        StreamCache cache = cachePart(content);

        assertTrue(cache.inMemory());
        assertArrayEquals(content, readFully(cache));
    }

    private StreamCache cachePart(byte[] content) throws Exception {
        Part part = mock(Part.class);
        when(part.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(part);

        FileUploadRouteBuilder.cachePartContent(exchange);

        return exchange.getIn().getHeader(FileUploadRouteBuilder.FILE_CONTENT_HEADER, StreamCache.class);
    }

    private static byte[] readFully(StreamCache cache) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        cache.writeTo(out);
        return out.toByteArray();
    }
}
