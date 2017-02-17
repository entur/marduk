package no.rutebanken.marduk.geocoder.netex.kartverket;

import no.rutebanken.marduk.geocoder.netex.TopographicPlaceReader;
import org.apache.commons.io.FileUtils;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.geojson.feature.FeatureJSON;
import org.opengis.feature.Feature;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.TopographicPlace;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;

public class GeoJsonTopographicPlaceReader implements TopographicPlaceReader {

	private File[] files;

	private static final String LANGUAGE = "en";

	private static final String PARTICIPANT_REF = "KVE";


	public GeoJsonTopographicPlaceReader(File... files) {
		this.files = files;

	}

	public void addToQueue(BlockingQueue<TopographicPlace> queue) throws IOException, InterruptedException {
		for (File file : files) {
			FeatureJSON fJson = new FeatureJSON();

			FeatureCollection collection = fJson.readFeatureCollection(FileUtils.openInputStream(file));

			FeatureIterator<Feature> itr = collection.features();
			while (itr.hasNext()) {
				queue.put((KartverketFeatureMapper.create(itr.next(), PARTICIPANT_REF)).toTopographicPlace());
			}
		}
	}

	@Override
	public String getParticipantRef() {
		return PARTICIPANT_REF;
	}

	@Override
	public MultilingualString getDescription() {
		return new MultilingualString().withLang(LANGUAGE).withValue("Kartverket administrative units");
	}
}
