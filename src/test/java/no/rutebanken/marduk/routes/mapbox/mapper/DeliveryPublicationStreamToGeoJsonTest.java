package no.rutebanken.marduk.routes.mapbox.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class DeliveryPublicationStreamToGeoJsonTest {


    private ZoneToGeoJsonFeatureMapper zoneToGeoJsonFeatureMapper = new ZoneToGeoJsonFeatureMapper();
    private StopPlaceToGeoJsonFeatureMapper stopPlaceToGeoJsonFeatureMapper = new StopPlaceToGeoJsonFeatureMapper(zoneToGeoJsonFeatureMapper);
    private DeliveryPublicationStreamToGeoJson deliveryPublicationStreamToGeoJson = new DeliveryPublicationStreamToGeoJson(stopPlaceToGeoJsonFeatureMapper);

    @Test
    public void transform() throws Exception {

        FileInputStream fileInputStream = new FileInputStream("src/test/resources/no/rutebanken/marduk/routes/mapbox/publication-delivery.xml");

        ByteArrayOutputStream byteArrayOutputStream = (ByteArrayOutputStream) deliveryPublicationStreamToGeoJson.transform(fileInputStream);

        FeatureCollection featureCollection =
                new ObjectMapper().readValue(byteArrayOutputStream.toString(), FeatureCollection.class);

        assertThat(featureCollection).isNotNull();
        assertThat(featureCollection.getFeatures())
                .isNotEmpty()
                .extracting(Feature::getId)
                    .containsExactly("NSR:StopPlace:1", "NSR:StopPlace:10");

        assertThat(featureCollection.getFeatures())
                .extracting(Feature::getGeometry).doesNotContainNull();

        List<String> titles = featureCollection.getFeatures()
                .stream()
                .map(Feature::getProperties)
                .flatMap(properties -> properties.entrySet().stream())
                .filter(entrySet -> entrySet.getKey().equals("title"))
                .map(Map.Entry::getValue)
                .map(object -> (String) object)
                .collect(toList());
        assertThat(titles)
                .contains("Drangedal stasjon", "Paradis");

    }
}