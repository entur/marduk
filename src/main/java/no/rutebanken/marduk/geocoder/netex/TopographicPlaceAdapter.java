package no.rutebanken.marduk.geocoder.netex;

import com.vividsolutions.jts.geom.Geometry;

public interface TopographicPlaceAdapter {
    enum Type {COUNTRY, COUNTY, LOCALITY, BOROUGH, NEIGHBOURHOOD}

    String getId();

    String getIsoCode();

    String getParentId();

    String getName();

    TopographicPlaceAdapter.Type getType();

    Geometry getDefaultGeometry();
}
