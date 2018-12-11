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

package no.rutebanken.marduk.routes.sftp;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_SKIP_STATUS_UPDATE_FOR_DUPLICATES;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

/**
 * Downloads file from lamassu, putting it in blob store, posting handle on queue.
 */
@Component
public class SftpReceiverRouteBuilder extends BaseRouteBuilder {

    @Value("${sftp.host}")
    private String sftpHost;

    @Value("${sftp.user:marduk}")
    private String sftpUser;

    @Value("${sftp.keyfile}")
    private String sftpKeyFile;

    @Value("${sftp.known.hosts.file}")
    private String knownHostsFile;

    @Value("${sftp.timetable.path:/incoming/timetable/}")
    private String timetablePath;


    private Logger logger = LoggerFactory.getLogger(this.getClass());


    @Autowired
    private ProviderRepository providerRepository;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("sftp://" + sftpUser + "@" + sftpHost + "?privateKeyFile=" + sftpKeyFile + "&knownHostsFile=" + knownHostsFile + "&recursive=true&sorter=#caseIdSftpSorter&delay={{sftp.delay}}&delete={{idempotent.skip:false}}&localWorkDirectory=files/tmp&connectTimeout=1000")
                .autoStartup("{{sftp.autoStartup:true}}")
                .transacted()
                .doTry() // <- doTry seems necessary for correct transactional handling. not sure why...
                .setHeader(FILE_SKIP_STATUS_UPDATE_FOR_DUPLICATES, simple("true"))
                .filter(simple("${header." + Exchange.FILE_NAME + "} contains '" + timetablePath + "'"))
                .process(e -> setHeadersFromFileName(e))
                .filter(simple("${header." + PROVIDER_ID + "} != null"))
                .process(e -> e.getIn().setHeader(CORRELATION_ID, UUID.randomUUID().toString()))
                .to("direct:filterDuplicateFile")
                .log(LoggingLevel.INFO, correlation() + "Received new file on sftp route. Storing file ${header." + Exchange.FILE_NAME + "}'")

                .setHeader(FILE_HANDLE, simple(Constants.BLOBSTORE_PATH_INBOUND + "${header." + CHOUETTE_REFERENTIAL + "}/${header." + CHOUETTE_REFERENTIAL + "}-${date:now:yyyyMMddHHmmss}-${header.CamelFileNameOnly}"))
                .log(LoggingLevel.INFO, correlation() + "File handle is: ${header." + FILE_HANDLE + "}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .to("direct:uploadBlob")

                .choice().when(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.enableAutoImport)
                .process(e -> JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.FILE_TRANSFER).state(JobEvent.State.STARTED).build())
                .to("direct:updateStatus")
                .log(LoggingLevel.INFO, correlation() + "Putting handle ${header." + FILE_HANDLE + "} on queue...")
                .to("activemq:queue:ProcessFileQueue")
                .otherwise()
                .log(LoggingLevel.INFO, "Do not initiate processing of  ${header." + FILE_HANDLE + "} as autoImport is not enabled for provider")
                .end()
                .routeId("sftp-gcs-route");


    }

    private void setHeadersFromFileName(Exchange e) {
        String fullFilePath = e.getIn().getHeader(Exchange.FILE_NAME, String.class);
        String[] parts = fullFilePath.split(timetablePath);
        String providerSftpAccount = parts[0];
        String fileName = parts[1];

        Optional<Provider> providerOpt = providerRepository.getProviders().stream().filter(candidate -> providerSftpAccount.equals(candidate.sftpAccount)).findFirst();

        if (providerOpt.isPresent()) {
            Provider provider = providerOpt.get();
            e.getIn().setHeader(PROVIDER_ID, provider.getId());
            e.getIn().setHeader(Constants.FILE_NAME, fileName);
            e.getIn().setHeader(CHOUETTE_REFERENTIAL, provider.getChouetteInfo().referential);
        } else {
            logger.warn("Found timetable file for unknown provider: " + fullFilePath);
        }
    }

}
