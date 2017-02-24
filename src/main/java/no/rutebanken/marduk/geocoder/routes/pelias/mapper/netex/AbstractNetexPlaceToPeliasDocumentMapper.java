package no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex;


import no.rutebanken.marduk.geocoder.routes.pelias.json.GeoPoint;
import no.rutebanken.marduk.geocoder.routes.pelias.json.Name;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import org.rutebanken.netex.model.LocationStructure;
import org.rutebanken.netex.model.Place_VersionStructure;

public abstract class AbstractNetexPlaceToPeliasDocumentMapper<T extends Place_VersionStructure> {

	private String participantRef;


	public AbstractNetexPlaceToPeliasDocumentMapper(String participantRef) {
		this.participantRef = participantRef;
	}

	public PeliasDocument toPeliasDocument(T place) {
		PeliasDocument document = new PeliasDocument(getLayer(place), participantRef, place.getId());

		document.setName(new Name(place.getName().getValue(), null));

		if (place.getCentroid() != null) {
			LocationStructure loc = place.getCentroid().getLocation();
			document.setCenterPoint(new GeoPoint(loc.getLatitude().doubleValue(), loc.getLongitude().doubleValue()));
		}

		populateDocument(place, document);

		return document;
	}

	protected abstract void populateDocument(T place, PeliasDocument document);

	protected abstract String getLayer(T place);

}
