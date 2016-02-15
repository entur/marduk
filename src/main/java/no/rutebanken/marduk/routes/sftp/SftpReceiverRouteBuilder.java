package no.rutebanken.marduk.routes.sftp;

import com.google.common.base.Strings;
import no.rutebanken.marduk.management.Provider;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.Status;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

import static no.rutebanken.marduk.Constants.*;

/**
 * Downloads file from lamassu, putting it in blob store, posting handle on queue.
 */
@Component
public class SftpReceiverRouteBuilder extends BaseRouteBuilder {

    @Value("${sftp.host}")
    private String sftpHost;

    @Value("${sftp.keyfile}")
    private String sftpKeyFile;

    @Override
    public void configure() throws Exception {
        super.configure();

        CamelContext context = getContext();

        List<Provider> providersWithSftpAccounts = getProviderRepository().getProvidersWithSftpAccounts();
        providersWithSftpAccounts.forEach(p -> {
            try {
                context.addRoutes(new DynamcSftpPollerRouteBuilder(context, p, sftpHost, sftpKeyFile));
            } catch (Exception e){
                throw new RuntimeException(e);
            }
        });

    }

    private static final class DynamcSftpPollerRouteBuilder extends RouteBuilder {
        private final Provider provider;
        private final String sftpHost;
        private final String sftpKeyFile;

        private DynamcSftpPollerRouteBuilder(CamelContext context, Provider provider, String sftpHost, String sftpKeyFile) {
            super(context);
            this.provider = provider;
            this.sftpHost = sftpHost;
            this.sftpKeyFile = sftpKeyFile;
        }

        @Override
        public void configure() throws Exception {
            from("sftp://" + provider.getSftpAccount() + "@" + sftpHost + "?privateKeyFile=" + sftpKeyFile + "&delay=30s&delete=true&localWorkDirectory=files/tmp")
                    .log(LoggingLevel.INFO, getClass().getName(), "Received file on sftp route for '" + provider.getSftpAccount() + "'. Storing file ...")
                    .setHeader(FILE_HANDLE, simple("inbound/" + provider.getId() + "-${date:now:yyyyMMddHHmmss}-${header.CamelFileNameOnly}"))
                    .log(LoggingLevel.INFO, getClass().getName(), "File handle is: ${header." + FILE_HANDLE + "}")
                    .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                    .to("direct:uploadBlob")
                    .setHeader(CORRELATION_ID, constant(System.currentTimeMillis()))
                    .log(LoggingLevel.INFO, getClass().getName(), "Putting handle ${header." + FILE_HANDLE + "} and provider ${header." + PROVIDER_ID + "} on queue...")
                    .to("activemq:queue:ProcessFileQueue");
        }
    }

}
