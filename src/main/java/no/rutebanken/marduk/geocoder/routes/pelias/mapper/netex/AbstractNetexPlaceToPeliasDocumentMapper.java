package no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex;


import net.opengis.gml._3.AbstractRingType;
import net.opengis.gml._3.LinearRingType;
import no.rutebanken.marduk.geocoder.routes.pelias.json.AddressParts;
import no.rutebanken.marduk.geocoder.routes.pelias.json.GeoPoint;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import org.apache.commons.collections.CollectionUtils;
import org.geojson.LngLatAlt;
import org.geojson.Polygon;
import org.rutebanken.netex.model.LocationStructure;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.Place_VersionStructure;
import org.rutebanken.netex.model.ValidBetween;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractNetexPlaceToPeliasDocumentMapper<T extends Place_VersionStructure> {

    private String participantRef;

    public AbstractNetexPlaceToPeliasDocumentMapper(String participantRef) {
        this.participantRef = participantRef;
    }

    public PeliasDocument toPeliasDocument(PlaceHierarchy<T> placeHierarchy) {
        T place = placeHierarchy.getPlace();
        if (!isValid(place)) {
            return null;
        }

        PeliasDocument document = new PeliasDocument(getLayer(place), place.getId());

        MultilingualString name = place.getName();
        if (name != null) {
            document.setDefaultNameAndPhrase(name.getValue());
            if (name.getLang() != null) {
                document.addName(name.getLang(), name.getValue());
            }
        }

        if (place.getCentroid() != null) {
            LocationStructure loc = place.getCentroid().getLocation();
            document.setCenterPoint(new GeoPoint(loc.getLatitude().doubleValue(), loc.getLongitude().doubleValue()));
        }

        if (place.getPolygon() != null) {
            // TODO issues with shape validation in elasticsearch. duplicate coords + intersections cause document to be discarded. is shape even used by pelias?
            document.setShape(toPolygon(place.getPolygon().getExterior().getAbstractRing().getValue()));
        }

        addIdToStreetNameToAvoidFalseDuplicates(place, document);

        populateDocument(placeHierarchy, document);

        return document;
    }

    protected boolean isValid(T place) {
        String layer = getLayer(place);

        if (layer == null) {
            return false;
        }

        if (!CollectionUtils.isEmpty(place.getValidBetween()) && place.getValidBetween().stream().noneMatch(vb -> isValidNow(vb))) {
            return false;
        }
        return true;
    }

    // Should compare instant with validbetween from/to in timezone defined in PublicationDelivery, but makes little difference in practice
    private boolean isValidNow(ValidBetween validBetween) {
        LocalDateTime now = LocalDateTime.now();
        if (validBetween != null) {
            if (validBetween.getFromDate() != null && validBetween.getFromDate().isAfter(now)) {
                return false;
            }

            if (validBetween.getToDate() != null && validBetween.getToDate().isBefore(now)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The Pelias APIs deduper will throw away results with identical name, layer, parent and address. Setting unique ID in street part of address to avoid unique
     * topographic places with identical names being deduped.
     */
    private void addIdToStreetNameToAvoidFalseDuplicates(T place, PeliasDocument document) {
        if (document.getAddressParts() == null) {
            document.setAddressParts(new AddressParts());
        }
        document.getAddressParts().setStreet("NOT_AN_ADDRESS-" + place.getId());
    }

    private Polygon toPolygon(AbstractRingType ring) {

        if (ring instanceof LinearRingType) {
            LinearRingType linearRing = (LinearRingType) ring;

            List<LngLatAlt> coordinates = new ArrayList<>();
            LngLatAlt coordinate = null;
            LngLatAlt prevCoordinate = null;
            for (Double val : linearRing.getPosList().getValue()) {
                if (coordinate == null) {
                    coordinate = new LngLatAlt();
                    coordinate.setLatitude(val);
                } else {
                    coordinate.setLongitude(val);
                    if (prevCoordinate == null || !equals(coordinate, prevCoordinate)) {
                        coordinates.add(coordinate);
                    }
                    prevCoordinate = coordinate;
                    coordinate = null;
                }
            }
            return new Polygon(coordinates);

        }
        return null;
    }

    private boolean equals(LngLatAlt coordinate, LngLatAlt other) {
        return other.getLatitude() == coordinate.getLatitude() && other.getLongitude() == coordinate.getLongitude();
    }

    protected abstract void populateDocument(PlaceHierarchy<T> placeHierarchy, PeliasDocument document);

    protected abstract String getLayer(T place);

}
