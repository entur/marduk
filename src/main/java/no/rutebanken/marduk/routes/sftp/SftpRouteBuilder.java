package no.rutebanken.marduk.routes.sftp;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.management.Provider;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static no.rutebanken.marduk.Constants.*;

/**
 * Downloads file from lamassu, putting it in blob store, posting handle on queue.
 */
@Component
public class SftpRouteBuilder extends BaseRouteBuilder {

    @Override
    public void configure() throws Exception {
        super.configure();

        CamelContext context = getContext();

        List<Provider> providersWithSftpAccounts = providerRepository.getProvidersWithSftpAccounts();
        providersWithSftpAccounts.forEach(p -> {
                try {
                    context.addRoutes(new DynamcSftpPollerRouteBuilder(context, p));
                } catch (Exception e){
                    throw new RuntimeException(e);
                }
            });

    }

    private static final class DynamcSftpPollerRouteBuilder extends RouteBuilder {
        private final Provider provider;

        private DynamcSftpPollerRouteBuilder(CamelContext context, Provider provider) {
            super(context);
            this.provider = provider;
        }

        @Override
        public void configure() throws Exception {
            from("sftp://" + provider.getSftpAccount() + "@lamassu:22?privateKeyFile=/opt/jboss/.ssh/lamassu.pem&delay=30s&delete=true&localWorkDirectory=files/tmp")
                    .log(LoggingLevel.INFO, getClass().getName(), "Received file on sftp route for '" + provider.getSftpAccount() + "'. Storing file ...")
                    .setHeader(FILE_HANDLE, simple(provider.getId() + "-${date:now:yyyyMMddHHmmss}-${header.CamelFileNameOnly}"))
                    .log(LoggingLevel.INFO, getClass().getName(), "File handle is: ${header." + FILE_HANDLE + "}")
                    .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                    .process(e -> {
                        e.getOut().setBody(new ByteArrayInputStream(e.getIn().getBody(byte[].class)), InputStream.class);
                        e.getOut().setHeaders(e.getIn().getHeaders());
                    })
                    .to("direct:uploadBlob")
                    .setHeader(PROVIDER_ID, simple("" + provider.getId()))
                    .to("activemq:queue:ProcessFileQueue");
        }
    }

}
