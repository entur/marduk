package no.rutebanken.marduk.geocoder.geojson;


import org.apache.commons.lang3.StringUtils;
import org.opengis.feature.simple.SimpleFeature;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class KartverketPlace extends AbstractKartverketGeojsonAdapter {

    private List<String> placeTypeWhiteList;

    public KartverketPlace(SimpleFeature feature, List<String> placeTypeWhiteList) {
        super(feature);
        this.placeTypeWhiteList = placeTypeWhiteList;
    }

    @Override
    public String getId() {
        return "" + getProperty("enh_ssr_id");
    }

    @Override
    public String getName() {
        return getProperty("enh_snavn");
    }

    @Override
    public String getParentId() {
        return StringUtils.leftPad("" + getProperty("enh_komm"), 4, "0");
    }

    @Override
    public AbstractKartverketGeojsonAdapter.Type getType() {
        return Type.PLACE;
    }

    @Override
    public boolean isValid() {
        if (!isValidType(getProperty("enh_navntype"))) {
            return false;
        }
        return KartverketFeatureSpellingStatusCode.isActive(getProperty("skr_snskrstat"));
    }

    private boolean isValidType(Long type) {
        if (placeTypeWhiteList == null) {
            return true;
        }
        return placeTypeWhiteList.contains(Objects.toString(type));
    }

    @Override
    public List<String> getCategories() {
        return Arrays.asList("" + getProperty("enh_navntype"));
    }
}
