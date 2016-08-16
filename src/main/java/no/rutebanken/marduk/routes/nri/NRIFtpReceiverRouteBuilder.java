package no.rutebanken.marduk.routes.nri;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

import java.util.UUID;

import org.apache.camel.LoggingLevel;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.remote.RemoteFile;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.BaseRouteBuilder;

/**
 * Downloads zip files from NRI ftp, sends to activemq
 */
@Component
@Configuration
public class NRIFtpReceiverRouteBuilder extends BaseRouteBuilder {

	@Override
	public void configure() throws Exception {
		from("ftp://{{nri.ftp.host}}/{{nri.ftp.folder}}?username={{nri.ftp.username}}&password={{nri.ftp.password}}&delay={{nri.ftp.delay}}&recursive=true&delete=false&filter=#regtoppFileFilter&sorter=#caseIdNriFtpSorter&ftpClient.controlEncoding=UTF-8&passiveMode=true&binary=true")
		.autoStartup("{{nri.ftp.autoStartup:true}}")
		.process(e -> {
            e.getIn().setHeader(CORRELATION_ID, UUID.randomUUID().toString());
		})
		.log(LoggingLevel.INFO, correlation()+"Received file ${header.CamelFileName} on NRI ftp route")
        .process(e -> {
    		RemoteFile<?> p = (RemoteFile<?>) e.getProperty(FileComponent.FILE_EXCHANGE_FILE);
    		String relativeFilePath = p.getRelativeFilePath();
    		String topFolder = relativeFilePath.substring(0, relativeFilePath.indexOf("/"));

    		Long providerId = mapRootFolderToProviderId(topFolder);
    		

        	if(providerId != null) {
	        	Provider provider = getProviderRepository().getProvider(providerId);
	    		String newFileName = relativeFilePath.replace(' ', '_').replace('/', '_');
	    		e.getIn().setHeader(Constants.FILE_NAME, newFileName);
	    		e.getIn().setHeader(FILE_HANDLE, 
	            		simple(Constants.BLOBSTORE_PATH_INBOUND_RECEIVED + provider.chouetteInfo.referential + "/" + provider.chouetteInfo.referential + "-${date:now:yyyyMMddHHmmss}-"+newFileName).evaluate(e, String.class));
	            e.getIn().setHeader(PROVIDER_ID, provider.id);
        	}
        })
        .filter(header(PROVIDER_ID).isNotNull())
        .log(LoggingLevel.INFO, correlation()+"File handle is: ${header." + FILE_HANDLE + "}")
        .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
        .to("direct:uploadBlob")
        .log(LoggingLevel.INFO, correlation()+"Putting handle ${header." + FILE_HANDLE + "}")
        .to("activemq:queue:ProcessFileQueue")
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
		case "Ruter (Akershus- og Oslo fylke)":
			return 2l;
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

}
