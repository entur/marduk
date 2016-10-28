package no.rutebanken.marduk.routes.chouette;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http4.HttpMethods;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;


@Component
public class ChouetteStatsRouteBuilder extends AbstractChouetteRouteBuilder {

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Value("${chouette.stats.validity.categories}")
    private String[] validityCategories;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:chouetteGetStats")
                .process( e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .removeHeaders("Camel*")
                .setBody(constant(""))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .setProperty("chouette_url", simple(chouetteUrl + "/chouette_iev/statistics/${header." + CHOUETTE_REFERENTIAL + "}/line?" + getValidityCategories()))
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "Calling chouette with ${property.chouette_url}")
                .toD("${exchangeProperty.chouette_url}");
    }

    private String getValidityCategories() {
        return Arrays.asList(validityCategories).stream().map(s -> "minDaysValidityCategory=" + s).collect(Collectors.joining("&"));
    }

}
