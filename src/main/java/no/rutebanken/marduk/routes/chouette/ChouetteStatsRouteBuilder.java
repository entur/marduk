package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.domain.Provider;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.*;


@Component
public class ChouetteStatsRouteBuilder extends AbstractChouetteRouteBuilder {

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Value("${chouette.stats.validity.categories}")
    private String[] validityCategories;

    @Value("${chouette.stats.days}")
    private int days;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:chouetteGetStatsSingleProvider")
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .removeHeaders("Camel*")
                .setBody(constant(""))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .setProperty("chouette_url", simple(chouetteUrl + "/chouette_iev/statistics/${header." + CHOUETTE_REFERENTIAL + "}/line?days=" + days + "&" + getValidityCategories()))
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "Calling chouette with ${property.chouette_url}")
                .toD("${exchangeProperty.chouette_url}");

        from("direct:chouetteGetStats")
                .removeHeaders("Camel*")
                .setBody(constant(""))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .process(e -> e.getIn().setHeader("refParam", getReferentialsAsParam(e)))
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Calling chouette with referentials prop: ${header.refParam}")
                .setProperty("chouette_url", simple(chouetteUrl + "/chouette_iev/statistics/line?days=" + days + "&" + getValidityCategories() + "${header.refParam}"))
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Calling chouette with ${property.chouette_url}")
                .toD("${exchangeProperty.chouette_url}")
                .unmarshal().json(JsonLibrary.Jackson, Map.class)
                .process(e -> e.getIn().setBody(mapReferentialToProviderId(e.getIn().getBody(Map.class))))
                .marshal().json(JsonLibrary.Jackson);
    }

    private Map<Long, Object> mapReferentialToProviderId(Map<String, Object> statsPerReferential) {
        log.info("Chouette line stats returned response: " + statsPerReferential);
        return getProviderRepository().getProviders().stream().filter(provider -> statsPerReferential.containsKey(provider.chouetteInfo.referential))
                       .collect(Collectors.toMap(Provider::getId, provider -> statsPerReferential.get(provider.chouetteInfo.referential)));
    }

    private String getReferentialsAsParam(Exchange e) {
        List<String> providerIds = e.getIn().getHeader(PROVIDER_IDS, List.class);

        Collection<Provider> providers;
        if (providerIds == null) {
            providers = getProviderRepository().getProviders();
        } else {
            providers = providerIds.stream().map(providerId -> getProviderRepository().getProvider(Long.valueOf(providerId))).collect(Collectors.toList());
        }

        StringBuilder sb = new StringBuilder();
        providers.stream().map(provider -> provider.chouetteInfo.referential).forEach(referential -> sb.append("&referential=").append(referential));

        return sb.toString();
    }

    private String getValidityCategories() {
        return Arrays.asList(validityCategories).stream().map(s -> "minDaysValidityCategory=" + s).collect(Collectors.joining("&"));
    }

}
