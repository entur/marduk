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

package no.rutebanken.marduk.rest;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.support.DefaultExchange;

/**
 * Builder for creating Camel Exchange objects with a fluent API.
 * Reduces boilerplate when setting up exchanges for route invocation.
 */
public class ExchangeBuilder {

    private final Exchange exchange;

    private ExchangeBuilder(CamelContext camelContext) {
        this.exchange = new DefaultExchange(camelContext);
    }

    public static ExchangeBuilder create(CamelContext camelContext) {
        return new ExchangeBuilder(camelContext);
    }

    public ExchangeBuilder withHeader(String key, Object value) {
        exchange.getIn().setHeader(key, value);
        return this;
    }

    public ExchangeBuilder withBody(Object body) {
        exchange.getIn().setBody(body);
        return this;
    }

    public ExchangeBuilder withPattern(ExchangePattern pattern) {
        exchange.setPattern(pattern);
        return this;
    }

    public Exchange build() {
        return exchange;
    }
}
