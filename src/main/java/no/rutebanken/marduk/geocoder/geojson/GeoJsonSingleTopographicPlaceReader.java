package no.rutebanken.marduk.geocoder.geojson;

import no.rutebanken.marduk.geocoder.netex.TopographicPlaceAdapter;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceMapper;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceReader;
import org.apache.commons.io.FileUtils;
import org.geotools.geojson.feature.FeatureJSON;
import org.opengis.feature.simple.SimpleFeature;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.TopographicPlace;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;

/**
 * For reading individual features from geojson files.
 */
public class GeoJsonSingleTopographicPlaceReader implements TopographicPlaceReader {

    private File[] files;

    private static final String LANGUAGE = "en";

    private static final String PARTICIPANT_REF = "WOF";


    public GeoJsonSingleTopographicPlaceReader(File... files) {
        this.files = files;
    }

    public void addToQueue(BlockingQueue<TopographicPlace> queue) throws IOException, InterruptedException {
        for (File file : files) {
            FeatureJSON fJson = new FeatureJSON();
            InputStream inputStream = FileUtils.openInputStream(file);
            SimpleFeature simpleFeature = fJson.readFeature(inputStream);

            TopographicPlaceAdapter adapter = GeojsonFeatureWrapperFactory.createWrapper(simpleFeature);
            TopographicPlace topographicPlace = new TopographicPlaceMapper(adapter, PARTICIPANT_REF).toTopographicPlace();

            if (topographicPlace != null) {
                queue.put(topographicPlace);
            }
            inputStream.close();
        }
    }

    @Override
    public String getParticipantRef() {
        return PARTICIPANT_REF;
    }

    @Override
    public MultilingualString getDescription() {
        return new MultilingualString().withLang(LANGUAGE).withValue("Whosonfirst neighbouring countries");
    }
}