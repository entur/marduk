package no.rutebanken.marduk.geocoder.geojson;


import org.apache.commons.lang3.StringUtils;
import org.opengis.feature.simple.SimpleFeature;

import java.util.List;
import java.util.Objects;

public class KartverketNeighbourhood extends AbstractKartverketGeojsonAdapter {

    private List<String> neighbourhoodTypeBlackList;

    public KartverketNeighbourhood(SimpleFeature feature, List<String> neighbourhoodTypeBlackList) {
        super(feature);
        this.neighbourhoodTypeBlackList = neighbourhoodTypeBlackList;
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
        return Type.NEIGHBOURHOOD;
    }

    @Override
    public boolean isValid() {
        if (!isValidType(getProperty("enh_navntype"))) {
            return false;
        }
        return KartverketFeatureSpellingStatusCode.isActive(getProperty("skr_snskrstat"));
    }

    private boolean isValidType(Long type) {
        if (neighbourhoodTypeBlackList == null) {
            return true;
        }
        return !neighbourhoodTypeBlackList.contains(Objects.toString(type));
    }
}
