package no.rutebanken.marduk.routes.sftp;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.Status;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

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

    @Value("${sftp.known.hosts.file}")
    private String knownHostsFile;

    @Override
    public void configure() throws Exception {
        super.configure();

        CamelContext context = getContext();

        //TODO Catch changes in sftp account, restart route with new config?
        Collection<Provider> providers = getProviderRepository().getProviders();
        providers.stream().filter(p -> p.sftpAccount != null).forEach(p -> {
            try {
                context.addRoutes(new DynamcSftpPollerRouteBuilder(p, sftpHost, sftpKeyFile, knownHostsFile));
            } catch (Exception e){
                throw new RuntimeException(e);
            }
        });

    }

    private static final class DynamcSftpPollerRouteBuilder extends BaseRouteBuilder {
        private final Provider provider;
        private final String sftpHost;
        private final String sftpKeyFile;
        private final String knownHostsFile;

        private DynamcSftpPollerRouteBuilder(Provider provider, String sftpHost, String sftpKeyFile, String knownHostsFile) {
            this.provider = provider;
            this.sftpHost = sftpHost;
            this.sftpKeyFile = sftpKeyFile;
            this.knownHostsFile = knownHostsFile;
        }

        @Override
        public void configure() throws Exception {
            from("sftp://" + provider.sftpAccount + "@" + sftpHost + "?privateKeyFile=" + sftpKeyFile + "&knownHostsFile=" + knownHostsFile + "&sorter=#caseIdSftpSorter&delay={{sftp.delay}}&delete={{idempotent.skip:false}}&localWorkDirectory=files/tmp&connectTimeout=1000")
					.autoStartup("{{sftp.autoStartup:true}}")
                    .setHeader(PROVIDER_ID, constant(provider.id))
                    .setHeader(Constants.FILE_NAME, header(Exchange.FILE_NAME))
                    .process(e -> e.getIn().setHeader(CORRELATION_ID, UUID.randomUUID().toString()))
                    .to("direct:filterDuplicateFile")
                    .log(LoggingLevel.INFO, correlation()+"Received new file on sftp route for '" + provider.sftpAccount + "'. Storing file ...")
                    .setHeader(FILE_HANDLE, simple(provider.chouetteInfo.referential + "/${header.CamelFileNameOnly}"))
                    .log(LoggingLevel.INFO, correlation()+"File handle is: ${header." + FILE_HANDLE + "}")
                    .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                    .to("direct:uploadBlob")
                    .process(e -> Status.builder(e).action(Status.Action.FILE_TRANSFER).state(Status.State.STARTED).build())
                    .to("direct:updateStatus")
                    .log(LoggingLevel.INFO, correlation()+"Putting handle ${header." + FILE_HANDLE + "} on queue...")
                    .to("activemq:queue:ProcessFileQueue")
                    .routeId("sftp-gcs-"+provider.chouetteInfo.referential);
        }
    }

}
