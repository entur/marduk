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

package no.rutebanken.marduk.routes.chouette;

import org.apache.camel.Exchange;
import org.apache.camel.component.http.HttpMethods;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.JSON_PART;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractChouetteRouteBuilderTest {

    // concrete subclass to reach the protected helpers
    private static final class TestChouetteRouteBuilder extends AbstractChouetteRouteBuilder {
    }

    private final AbstractChouetteRouteBuilder routeBuilder = new TestChouetteRouteBuilder();
    private DefaultCamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    // camel-http 4.18 cannot convert a constant(...) Expression on HTTP_METHOD; must be a plain value.
    @Test
    void httpMethodHeaderIsAPlainHttpMethod() {
        Exchange exchange = exchangeWithJsonPart();

        routeBuilder.toGenericChouetteMultipart(exchange);

        HttpMethods httpMethod = assertDoesNotThrow(
                () -> exchange.getMessage().getHeader(Exchange.HTTP_METHOD, HttpMethods.class),
                "HTTP_METHOD must be a plain HttpMethods value, not a constant(...) Expression");
        assertEquals(HttpMethods.POST, httpMethod);
    }

    // Same anti-pattern: CONTENT_TYPE must be a plain String, not a simple(...) Expression.
    @Test
    void contentTypeHeaderIsAPlainString() {
        Exchange genericExchange = exchangeWithJsonPart();
        routeBuilder.toGenericChouetteMultipart(genericExchange);
        assertEquals("multipart/form-data", genericExchange.getMessage().getHeader(Exchange.CONTENT_TYPE));

        Exchange importExchange = exchangeWithJsonPart();
        importExchange.getIn().setHeader(FILE_NAME, "netex.zip");
        importExchange.getIn().setBody("payload".getBytes(StandardCharsets.UTF_8));
        routeBuilder.toImportMultipart(importExchange);
        assertEquals("multipart/form-data", importExchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
    }

    private Exchange exchangeWithJsonPart() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(JSON_PART, "{}".getBytes(StandardCharsets.UTF_8));
        return exchange;
    }
}
