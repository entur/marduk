package no.rutebanken.marduk.routes.etcd;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.etcd.json.EtcdResponse;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Utils.getHttp4;


/**
 * Get/ set values in etcd. Not using camel-etcd because timeout does not work (hangs indefinitely) with underlying etcd4j lib.
 */
@Component
@ConditionalOnProperty(name = "etcd.in.memory", havingValue = "false", matchIfMissing = true)
public class EtcdRouteBuilder extends BaseRouteBuilder {

    @Value("${etcd.url}")
    private String etcdUrl;


    @Override
    public void configure() throws Exception {

        from("direct:getEtcdValue")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.GET))
                .doTry()
                .toD(getHttp4(etcdUrl) + "${header." + Constants.ETCD_KEY + "}")
                .unmarshal().json(JsonLibrary.Jackson, EtcdResponse.class)

                .process(e -> e.getIn().setBody(e.getIn().getBody(EtcdResponse.class).node.value))
                .doCatch(HttpOperationFailedException.class).onWhen(exchange -> {
            HttpOperationFailedException ex = exchange.getException(HttpOperationFailedException.class);
            return (ex.getStatusCode() == 404);
        })
                .log(LoggingLevel.INFO, "No value found in etcd for key: ${header." + Constants.ETCD_KEY + "}. Returning null")
                .setBody(constant(null))
                .end()

                .routeId("etcd-get-value");

        from("direct:setEtcdValue")
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.PUT))
                .setProperty("etcdUrl", simple(getHttp4(etcdUrl) + "${header." + Constants.ETCD_KEY + "}?value=${body}"))
                .setBody(constant(null))
                .toD("${exchangeProperty.etcdUrl}")
                .routeId("set-sync-status-until");
    }
}
