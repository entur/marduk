package no.rutebanken.marduk.routes.sftp;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

import java.util.Collection;
import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.BaseRouteBuilder;

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

        //TODO Catch changes in sftp account, restart route with new config?
        Collection<Provider> providers = getProviderRepository().getProviders();
        providers.stream().filter(p -> p.sftpAccount != null).forEach(p -> {
            try {
                context.addRoutes(new DynamcSftpPollerRouteBuilder(context, p, sftpHost, sftpKeyFile));
            } catch (Exception e){
                throw new RuntimeException(e);
            }
        });
    }

    private static final class DynamcSftpPollerRouteBuilder extends BaseRouteBuilder {
        private final Provider provider;
        private final String sftpHost;
        private final String sftpKeyFile;

        private DynamcSftpPollerRouteBuilder(CamelContext context, Provider provider, String sftpHost, String sftpKeyFile) {
            this.provider = provider;
            this.sftpHost = sftpHost;
            this.sftpKeyFile = sftpKeyFile;
        }

        @Override
        public void configure() throws Exception {
            from("sftp://" + provider.sftpAccount + "@" + sftpHost + "?privateKeyFile=" + sftpKeyFile + "&sorter=#caseIdSftpSorter&delay=30s&delete=true&localWorkDirectory=files/tmp&connectTimeout=1000")
					.autoStartup("{{sftp.autoStartup:true}}")
				    .process(e -> e.getIn().setHeader(CORRELATION_ID, UUID.randomUUID().toString()))
                    .log(LoggingLevel.INFO, correlation()+"Received file on sftp route for '" + provider.sftpAccount + "'. Storing file ...")
                    .setHeader(FILE_HANDLE, simple(Constants.BLOBSTORE_PATH_INBOUND_RECEIVED + provider.chouetteInfo.referential + "/" + provider.chouetteInfo.referential + "-${date:now:yyyyMMddHHmmss}-${header.CamelFileNameOnly}"))
                    .setHeader(PROVIDER_ID, constant(provider.id))
                    .log(LoggingLevel.INFO, correlation()+"File handle is: ${header." + FILE_HANDLE + "}")
                    .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                    .to("direct:uploadBlob")
                    .log(LoggingLevel.INFO, correlation()+"Putting handle ${header." + FILE_HANDLE + "} on queue...")
                    .setHeader(Constants.FILE_NAME,exchangeProperty(Exchange.FILE_NAME))
                    .to("activemq:queue:ProcessFileQueue")
                    .routeId("sftp-gcs-"+provider.chouetteInfo.referential);
        }
    }

}
