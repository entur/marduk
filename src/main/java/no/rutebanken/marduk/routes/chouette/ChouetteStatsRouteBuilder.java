package no.rutebanken.marduk.routes.chouette;

import com.google.common.base.Joiner;
import no.rutebanken.marduk.domain.Provider;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;
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
                .setProperty("chouette_url", simple(chouetteUrl + "/chouette_iev/statistics/line?days=" + days + "&" + getValidityCategories() + "${header.refParam}"))
                .log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "Calling chouette with ${property.chouette_url}")
                .toD("${exchangeProperty.chouette_url}")
                .unmarshal().json(JsonLibrary.Jackson, Map.class)
                .process(e -> e.getIn().setBody(mapReferentialToProviderId(e.getIn().getBody(Map.class))))
                .marshal().json(JsonLibrary.Jackson);
    }

    private Map<Long, Object> mapReferentialToProviderId(Map<String, Object> statsPerReferential) {
        return getProviderRepository().getProviders().stream().filter(provider -> statsPerReferential.containsKey(provider.chouetteInfo.referential))
                       .collect(Collectors.toMap(Provider::getId, provider -> statsPerReferential.get(provider.chouetteInfo.referential)));
    }

    private String getReferentialsAsParam(Exchange e) {
        List<String> providerIds = e.getIn().getHeader(PROVIDER_IDS, List.class);
        String filter = e.getIn().getHeader("filter", String.class);

        List<String> referentials = getProviderRepository().getProviders().stream().filter(provider -> isMatch(provider, filter, providerIds)).
                                                                                                                                                      map(provider -> provider.chouetteInfo.referential).collect(Collectors.toList());
        return "&referentials=" + Joiner.on(",").join(referentials);
    }

    boolean isMatch(Provider provider, String filter, List<String> whiteListedProviderIds) {
        boolean match = true;
        if (StringUtils.hasLength(filter) && !"all".equalsIgnoreCase(filter)) {
            if ("level1".equalsIgnoreCase(filter)) {
                match &= provider.getChouetteInfo().migrateDataToProvider != null;
            } else if ("level2".equalsIgnoreCase(filter)) {
                match &= provider.getChouetteInfo().migrateDataToProvider == null;
            } else {
                match = false;
            }
        }

        if (!CollectionUtils.isEmpty(whiteListedProviderIds)) {
            match &= whiteListedProviderIds.contains(provider.id.toString());
        }
        return match;
    }

    private String getValidityCategories() {
        return Arrays.asList(validityCategories).stream().map(s -> "minDaysValidityCategory=" + s).collect(Collectors.joining("&"));
    }

}
