package no.rutebanken.marduk.geocoder.geojson;

import com.google.common.collect.Sets;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceAdapter;
import org.opengis.feature.simple.SimpleFeature;

import java.util.List;
import java.util.Set;

public class WhosOnFirstCountry extends AbstractGeojsonAdapter implements TopographicPlaceAdapter {

    public static final Set<String> TYPES = Sets.newHashSet("Country", "Sovereign country");

    public WhosOnFirstCountry(SimpleFeature feature) {
        super(feature);
    }

    @Override
    public String getId() {
        return getProperty("wof:id").toString();
    }

    @Override
    public String getIsoCode() {
        return getProperty("ne:iso_a3");
    }

    @Override
    public String getParentId() {
        return null;
    }

    @Override
    public String getName() {
        List<String> names = getProperty("name:eng_x_preferred");
        return names.get(0);
    }

    @Override
    public Type getType() {
        return Type.COUNTRY;
    }

}
