package no.rutebanken.marduk.geocoder.routes.pelias.mapper;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import no.rutebanken.marduk.geocoder.GeoCoderConstants;
import no.rutebanken.marduk.geocoder.geojson.KartverketLocality;
import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.rutebanken.marduk.geocoder.routes.pelias.json.GeoPoint;
import no.rutebanken.marduk.geocoder.routes.pelias.json.Parent;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import no.rutebanken.marduk.geocoder.services.AdminUnitRepository;
import org.apache.camel.Body;
import org.apache.camel.ExchangeProperty;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class PeliasIndexParentInfoEnricher {

	private GeometryFactory geometryFactory = new GeometryFactory();

	/**
	 * Enrich indexing commands with parent info if missing.
	 */
	public void addMissingParentInfo(@Body Collection<ElasticsearchCommand> commands,
			                                @ExchangeProperty(value = GeoCoderConstants.GEOCODER_ADMIN_UNIT_REPO) AdminUnitRepository adminUnitRepository) {
		commands.forEach(c -> addMissingParentInfo(c, adminUnitRepository));
	}

	void addMissingParentInfo(ElasticsearchCommand command, AdminUnitRepository adminUnitRepository) {
		if (!(command.getSource() instanceof PeliasDocument)) {
			return;
		}
		PeliasDocument peliasDocument = (PeliasDocument) command.getSource();
		Parent parent = peliasDocument.getParent();


		GeoPoint centerPoint = peliasDocument.getCenterPoint();
		if ((parent == null || parent.getLocalityId() == null) && centerPoint != null) {
			KartverketLocality locality = adminUnitRepository.getLocality(new GeometryFactory().createPoint(new Coordinate(centerPoint.getLon(), centerPoint.getLat())));
			if (locality != null) {
				if (parent == null) {
					parent = new Parent();
					peliasDocument.setParent(parent);
				}
				parent.setLocalityId(locality.getId());
				parent.setCountyId(locality.getParentId());
			}
		}


		if (parent != null) {
			if (parent.getCountyId() != null && parent.getCounty() == null) {
				parent.setCounty(adminUnitRepository.getAdminUnitName(parent.getCountyId()));
			}
			if (parent.getLocalityId() != null && parent.getLocality() == null) {
				parent.setLocality(adminUnitRepository.getAdminUnitName(parent.getLocalityId()));
			}
			if (parent.getBoroughId() != null && parent.getBorough() == null) {
				parent.setBorough(adminUnitRepository.getAdminUnitName(parent.getBoroughId()));
			}


		}
	}

}
