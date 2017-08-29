package no.rutebanken.marduk.geocoder.geojson;

import com.google.common.collect.Sets;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceAdapter;
import org.opengis.feature.simple.SimpleFeature;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        return getName("nno");
    }

    @Override
    public Type getType() {
        return Type.COUNTRY;
    }


    @Override
    public Map<String, String> getAlternativeNames() {
        Map<String, String> alternativeNames = new HashMap<>();

        alternativeNames.put("en", getName("eng"));

        return alternativeNames;
    }

    @Override
    public String getCountryRef() {
        return getProperty("ne:iso_a2");
    }

    private String getName(String lang) {
        List<String> names = getProperty("name:" + lang + "_x_preferred");
        if (CollectionUtils.isEmpty(names)) {
            return null;
        }
        return names.get(0);
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public List<String> getCategories() {
        return null;
    }
}
