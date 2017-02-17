package no.rutebanken.marduk.geocoder.services;


import no.jskdata.Downloader;
import no.jskdata.GeoNorgeDownloadAPI;
import no.jskdata.KartverketDownload;
import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;

public class KartverketServiceTest {

	@Test
	public void testAPIDownloaderIsUsedForUUIDs() {
		Downloader downloader = new KartverketService().getDownloader(UUID.randomUUID().toString(), null);
		Assert.assertTrue(downloader instanceof GeoNorgeDownloadAPI);
	}

	@Test
	public void testWebPageDownloaderIsUsedForNonUUIDs() {
		Downloader downloader = new KartverketService().getDownloader("not-a-uuid", null);
		Assert.assertTrue(downloader instanceof KartverketDownload);
	}
}
