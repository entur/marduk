package no.rutebanken.marduk.geocoder.netex.geojson;

import no.rutebanken.marduk.geocoder.geojson.GeojsonFeatureWrapperFactory;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceAdapter;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceMapper;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceReader;
import org.apache.commons.io.FileUtils;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.simple.SimpleFeatureImpl;
import org.geotools.geojson.feature.FeatureJSON;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.SimpleFeature;
import org.rutebanken.netex.model.TopographicPlace;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;

/**
 * For reading collections of features from geojson files.
 */
public class GeoJsonCollectionTopographicPlaceReader implements TopographicPlaceReader {

	private File[] files;

	private static final String LANGUAGE = "en";

	private static final String PARTICIPANT_REF = "KVE";


	public GeoJsonCollectionTopographicPlaceReader(File... files) {
		this.files = files;
	}

	public void addToQueue(BlockingQueue<TopographicPlace> queue) throws IOException, InterruptedException {
		for (File file : files) {
			FeatureJSON fJson = new FeatureJSON();
			FeatureIterator<org.opengis.feature.simple.SimpleFeature> itr = fJson.streamFeatureCollection(FileUtils.openInputStream(file));

			while (itr.hasNext()) {
				TopographicPlaceAdapter adapter = GeojsonFeatureWrapperFactory.createWrapper(itr.next());
				TopographicPlace topographicPlace = new TopographicPlaceMapper(adapter, PARTICIPANT_REF).toTopographicPlace();

				if (topographicPlace != null) {
					queue.put(topographicPlace);
				}
			}
			itr.close();
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
