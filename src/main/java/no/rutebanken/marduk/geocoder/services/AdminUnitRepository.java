package no.rutebanken.marduk.geocoder.services;


import com.vividsolutions.jts.geom.Point;
import no.rutebanken.marduk.geocoder.geojson.KartverketLocality;

public interface AdminUnitRepository {

	String getAdminUnitName(String id);

	KartverketLocality getLocality(Point point);

}
