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

package no.rutebanken.marduk.routes.nri;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.remote.RemoteFile;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_SKIP_STATUS_UPDATE_FOR_DUPLICATES;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

/**
 * Downloads zip files from NRI ftp, sends to activemq
 */
@Component
@Configuration
public class NRIFtpReceiverRouteBuilder extends BaseRouteBuilder {

    @Value("${nri.ftp.file.age.filter.months:3}")
    private int fileAgeFilterMonths;

    @Value("${nri.ftp.auto.import:false}")
    private boolean autoImport;

    @Override
    public void configure() throws Exception {
        singletonFrom("ftp://{{nri.ftp.host}}/{{nri.ftp.folder}}?username={{nri.ftp.username}}&password={{nri.ftp.password}}&filter=#ftpFileFilter&delay={{nri.ftp.delay:1h}}&initialDelay={{nri.ftp.initialDelay:1m}}&recursive=true&delete=false&sorter=#caseIdNriFtpSorter&ftpClient.controlEncoding=UTF-8&passiveMode=true&binary=true")
                .autoStartup("{{nri.ftp.autoStartup:true}}")
                .filter(e -> shouldFileBeHandled(e))
                .setHeader(CORRELATION_ID,constant(UUID.randomUUID().toString()))
                .process(e -> {
                    RemoteFile<FTPFile> p = e.getProperty(FileComponent.FILE_EXCHANGE_FILE, RemoteFile.class);
                    String relativeFilePath = p.getRelativeFilePath();
                    String topFolder = relativeFilePath.substring(0, relativeFilePath.indexOf("/"));

                    Long providerId = mapRootFolderToProviderId(topFolder);


                    if (providerId != null) {
                        Provider provider = getProviderRepository().getProvider(providerId);
                        String newFileName = relativeFilePath.substring(relativeFilePath.indexOf("/") + 1).replace(' ', '_').replace('/', '_');
                        e.getIn().setHeader(Constants.FILE_NAME, newFileName);
                        e.getIn().setHeader(FILE_HANDLE,
                                simple(Constants.BLOBSTORE_PATH_INBOUND + provider.chouetteInfo.referential + "/" + provider.chouetteInfo.referential + "-${date:now:yyyyMMddHHmmss}-" + newFileName).evaluate(e, String.class));
                        e.getIn().setHeader(PROVIDER_ID, provider.id);
                    }
                })
                .filter(header(PROVIDER_ID).isNotNull())
                .setHeader(FILE_SKIP_STATUS_UPDATE_FOR_DUPLICATES, simple("true"))
                .to("direct:filterDuplicateFile")
                .log(LoggingLevel.INFO, correlation() + "Received new file ${header.CamelFileName} on NRI ftp route")
                .log(LoggingLevel.INFO, correlation() + "File handle is: ${header." + FILE_HANDLE + "}")
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .to("direct:uploadBlob")

                .choice().when(e-> autoImport && getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID,Long.class)).chouetteInfo.enableAutoImport)
                .log(LoggingLevel.INFO, correlation() + "Putting handle ${header." + FILE_HANDLE + "}")
                .to("activemq:queue:ProcessFileQueue")
                .otherwise()
                .log(LoggingLevel.INFO, "Do not initiate processing of  ${header." + FILE_HANDLE + "} as autoImport is not enabled globally and for provider")
                .end()
                .routeId("nri-ftp-activemq");
    }

    private Long mapRootFolderToProviderId(String topFolder) {

        switch (topFolder) {
            case "Agder kollektivtrafikk (Aust- og Vest-Agder fylke)":
                return 7l;
            case "AtB (Sør-Trøndelag fylke)":
                return 12l;
            case "Brakar (Buskerud fylke)":
                return 5l;
            case "Fram (Møre og Romsdal fylke)":
                return 11l;
            case "Hedmark-Trafikk (Hedmark)":
                return 3l;
            case "Jotunheimen og Valdresruten Bilselskap":
                return 4l;
            case "NTFK (Nord-Trøndelag fylke)":
                return 13l;
            case "Nordland fylkeskommune (Nordland fylke)":
                return 14l;
//            case "Ruter (Akershus- og Oslo fylke)":
//                return 2l;
            case "Snelandia (Finnmark fylke)":
                return 16l;
            case "Sogn og Fjordane fylkeskommune (Sogn og Fjordane fylke)":
                return 10l;
            case "Telemark Bilruter AS":
                return 6l;
            case "Telemark Kollektivtrafikk (Telemark fylke)":
                return 18l;
            case "Troms fylkestrafikk (Troms fylke)":
                return 15l;
            case "Trønderbilene AS":
                return 13l;
            case "Vestfold kollektivtrafikk (Vestfold fylke)":
                return 6l;
            case "Østfold kollektivtrafikk (Østfold fylke)":
                return 1l;
            case "Kolumbus (Rogaland fylke)":
                return 8l;
            case "Opplandstrafikk (Oppland fylke)":
                return 4l;
            case "Jernbane":
//			return "tog";
            case "Skyss (Hordaland fylke)":
//			return "hordaland";

        }

        // The rest
        return null;
    }

    private boolean shouldFileBeHandled(Exchange e) {
        Date date = e.getIn().getHeader(Exchange.FILE_LAST_MODIFIED, Date.class);
        if (date == null || fileAgeFilterMonths < 1) {
            return true;
        }

        return date.after(DateUtils.addMonths(new Date(), -fileAgeFilterMonths));
    }

}
