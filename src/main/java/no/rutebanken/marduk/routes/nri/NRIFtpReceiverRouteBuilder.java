package no.rutebanken.marduk.routes.nri;

import java.util.Map;

import org.apache.camel.Headers;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Properties;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.remote.RemoteFile;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Downloads zip files from NRI ftp, sends to internal SFTP server
 */
@Component
@Configuration
public class NRIFtpReceiverRouteBuilder extends RouteBuilder {

	@Override
	public void configure() throws Exception {
		from("ftp://{{nri.ftp.host}}/{{nri.ftp.folder}}?username={{nri.ftp.username}}&password={{nri.ftp.password}}&delay={{nri.ftp.delay}}&recursive=true&delete=false&localWorkDirectory=files/nritmp&filter=#regtoppFileFilter&ftpClient.controlEncoding=UTF-8&passiveMode=true")
				.log(LoggingLevel.INFO, getClass().getName(), "Received file on NRI ftp route. Forwarding to sftp")
				.setProperty("sftp.host", simple("{{sftp.host}}"))
				.setProperty("sftp.keyfile", simple("{{sftp.keyfile}}"))
				.dynamicRouter(method(NRIFtpReceiverRouteBuilder.class, "slip"))
				.routeId("nri-ftp-sftp");
	}

	/**
	 * Use this method to compute dynamic where we should route next.
	 *
	 * @param body
	 *            the message body
	 * @param properties
	 *            the exchange properties where we can store state between
	 *            invocations
	 * @return endpoints to go, or <tt>null</tt> to indicate the end
	 */
	public static String slip(Object body, @Properties Map<String, Object> properties, @Headers Map<String,Object> outHeaders) {

		// Dynamic router must return null when done

		if (properties.get("completed") == null) {

			properties.put("completed", Boolean.TRUE);

			RemoteFile p = (RemoteFile) properties.get("CamelFileExchangeFile");
			String relativeFilePath = p.getRelativeFilePath();

			String topFolder = relativeFilePath.substring(0, relativeFilePath.indexOf("/"));

			String username = mapRootFolderToSftpUsername(topFolder);

			if (username != null) {
				
				String newFileName = relativeFilePath.replace(' ', '_').replace('/', '_');
				outHeaders.put("CamelFileName",newFileName);
				
				return "sftp://" + username + "@" + properties.get("sftp.host")+"?privateKeyFile=" + properties.get("sftp.keyfile") ;
			} 
		}
		return null;
	}

	private static String mapRootFolderToSftpUsername(String topFolder) {

		switch (topFolder) {
		case "Agder kollektivtrafikk (Aust- og Vest-Agder fylke)":
			return "agder";
		case "AtB (Sør-Trøndelag fylke)":
			return "sor-trondelag";
		case "Brakar (Buskerud fylke)":
			return "buskerud";
		case "Fram (Møre og Romsdal fylke)":
			return "more-romsdal";
		case "Hedmark-Trafikk (Hedmark)":
			return "hedmark";
		case "Jernbane":
			return "tog";
		case "Jotunheimen og Valdresruten Bilselskap":
			return "oppland";
		case "NTFK (Nord-Trøndelag fylke)":
			return "nord-trondelag";
		case "Nordland fylkeskommune (Nordland fylke)":
			return "nordland";
		case "Ruter (Akershus- og Oslo fylke)":
			return "oslo-akershus";
		case "Skyss (Hordaland fylke)":
			return "hordaland";
		case "Snelandia (Finnmark fylke)":
			return "finnmark";
		case "Sogn og Fjordane fylkeskommune (Sogn og Fjordane fylke)":
			return "sogn-fjordane";
		case "Telemark Bilruter AS":
			return "vestfold-telemark";
		case "Telemark Kollektivtrafikk (Telemark fylke)":
			return "vestfold-telemark";
		case "Troms fylkestrafikk (Troms fylke)":
			return "troms";
		case "Trønderbilene AS":
			return "sor-trondelag";
		case "Vestfold kollektivtrafikk (Vestfold fylke)":
			return "vestfold-telemark";
		case "Østfold kollektivtrafikk (Østfold fylke)":
			return "ostfold";

		}

		// The rest
		return null;
	}

}
