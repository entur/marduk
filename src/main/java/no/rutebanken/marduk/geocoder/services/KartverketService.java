package no.rutebanken.marduk.geocoder.services;

import no.jskdata.DefaultReceiver;
import no.jskdata.Downloader;
import no.jskdata.GeoNorgeDownloadAPI;
import no.jskdata.KartverketDownload;
import no.rutebanken.marduk.Constants;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class KartverketService {

	@Value("${kartverket.username}")
	private String username;

	@Value("${kartverket.password}")
	private String password;


	public List<File> downloadFiles(@Header(value = Constants.KARTVERKET_DATASETID) String dataSetId,
			                               @Header(value = Constants.KARTVERKET_FORMAT) String format,
			                               @Header(value = Exchange.FILE_PARENT) String localDownloadDir) {
		Downloader kd = getDownloader(dataSetId, format);

		return downloadFilesInternal(dataSetId, kd, localDownloadDir);
	}


	private List<File> downloadFilesInternal(String dataSetId, Downloader kd, String localDownloadDir) {
		List<File> files = new ArrayList<>();
		try {
			kd.login();
			kd.dataset(dataSetId);
			kd.download(new DefaultReceiver() {

				@Override
				public void receive(String fileName, InputStream in) throws IOException {
					File file = new File(localDownloadDir + "/" + fileName);
					FileUtils.writeByteArrayToFile(file, IOUtils.toByteArray(in));
					files.add(file);
				}
			});
		} catch (IOException ioException) {
			throw new RuntimeException("IO Exception downloading files from Kartverket for dataSetId: " + dataSetId, ioException);
		}
		return files;
	}

	Downloader getDownloader(String dataSetId, String format) {
		Downloader kd;
		if (isUUID(dataSetId)) {
			// UUIDs are used in the API
			kd = new GeoNorgeDownloadAPI();
			if (format != null) {
				kd.setFileNameFilter(n -> n.contains(format));
			}
		} else {
			kd = new KartverketDownload(username, password);
		}
		return kd;
	}

	private boolean isUUID(String dataSetId) {
		try {
			UUID.fromString(dataSetId);
		} catch (IllegalArgumentException notUUIDException) {
			return false;
		}
		return true;
	}


}
