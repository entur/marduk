package no.rutebanken.marduk.geocoder.routes.pelias.mapper;

import no.rutebanken.marduk.geocoder.GeoCoderConstants;
import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.rutebanken.marduk.geocoder.routes.pelias.json.Parent;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import no.rutebanken.marduk.geocoder.services.AdminUnitRepository;
import org.apache.camel.Body;
import org.apache.camel.ExchangeProperty;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class PeliasIndexParentInfoEnricher {

	/**
	 * Enrich indexing commands with parent info if missing.
	 */
	public void addMissingParentInfo(@Body Collection<ElasticsearchCommand> commands,
			                                @ExchangeProperty(value = GeoCoderConstants.GEOCODER_ADMIN_UNIT_REPO) AdminUnitRepository adminUnitRepository) {
		commands.forEach(c -> addMissingParentInfo(c, adminUnitRepository));
	}

	void addMissingParentInfo(ElasticsearchCommand command, AdminUnitRepository adminUnitRepository) {
		if (!(command.getSource() instanceof PeliasDocument)){
			return;
		}
		PeliasDocument peliasDocument = (PeliasDocument) command.getSource();
		Parent parent = peliasDocument.getParent();

		if (parent != null) {
			if (size(parent.getCountyId()) > size(parent.getCounty())) {
				parent.getCountyId().stream().forEach(countyId -> parent.addCounty(adminUnitRepository.getAdminUnitName(countyId)));
			}
			if (size(parent.getLocalityId()) > size(parent.getLocality())) {
				parent.getLocalityId().stream().forEach(localityId -> parent.addLocality(adminUnitRepository.getAdminUnitName(localityId)));
			}
			if (size(parent.getBoroughId()) > size(parent.getBorough())) {
				parent.getBoroughId().stream().forEach(boroughId -> parent.addBorough(adminUnitRepository.getAdminUnitName(boroughId)));
			}

		}
	}

	private int size(Collection<?> coll) {
		return coll == null ? 0 : coll.size();
	}
}
