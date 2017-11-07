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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class AbstractNetexPlaceToPeliasDocumentMapper<T extends Place_VersionStructure> {

    private String participantRef;

    public AbstractNetexPlaceToPeliasDocumentMapper(String participantRef) {
        this.participantRef = participantRef;
    }

    /**
     * Map single place hierarchy to (potentially) multiple pelias documents, one per alias/alternative name.
     *
     * Pelias does not yet support queries in multiple languages / for aliases. When support for this is ready this mapping should be
     * refactored to produce a single document per place hierarchy.
     */
    public List<PeliasDocument> toPeliasDocuments(PlaceHierarchy<T> placeHierarchy) {
        T place = placeHierarchy.getPlace();
        if (!isValid(place)) {
            return new ArrayList<>();
        }
        AtomicInteger cnt = new AtomicInteger();

        return getNames(placeHierarchy).stream().map(name -> toPeliasDocument(placeHierarchy, name, cnt.getAndAdd(1))).collect(Collectors.toList());
    }

    private PeliasDocument toPeliasDocument(PlaceHierarchy<T> placeHierarchy, MultilingualString name, int idx) {
        T place = placeHierarchy.getPlace();

        String idSuffix = idx > 0 ? "-" + idx : "";

        PeliasDocument document = new PeliasDocument(getLayer(place), place.getId() + idSuffix);
        if (name != null) {
            document.setDefaultNameAndPhrase(name.getValue());
        }

        // Add official name as display name. Not a part of standard pelias model, will be copied to name.default before deduping and labelling in Entur-pelias API.
        MultilingualString displayName = getDisplayName(placeHierarchy);
        if (displayName != null) {
            document.getNameMap().put("display", displayName.getValue());
            if (displayName.getLang() != null) {
                document.addName(displayName.getLang(), displayName.getValue());
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

    /**
     * Get name from current place or, if not set, on closest parent with name set.
     */
    protected MultilingualString getDisplayName(PlaceHierarchy<T> placeHierarchy) {
        if (placeHierarchy.getPlace().getName() != null) {
            return placeHierarchy.getPlace().getName();
        }
        if (placeHierarchy.getParent() != null) {
            return getDisplayName(placeHierarchy.getParent());
        }
        return null;
    }


    protected abstract List<MultilingualString> getNames(PlaceHierarchy<T> placeHierarchy);

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

    protected static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }

    protected abstract void populateDocument(PlaceHierarchy<T> placeHierarchy, PeliasDocument document);

    protected abstract String getLayer(T place);

}
