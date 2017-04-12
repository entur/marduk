package no.rutebanken.marduk.geocoder.services;


import com.vividsolutions.jts.geom.Point;
import no.rutebanken.marduk.geocoder.geojson.KartverketLocality;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceAdapter;

public interface AdminUnitRepository {

	String getAdminUnitName(String id);

	TopographicPlaceAdapter getLocality(Point point);

}
