package no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex;


import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex.boost.StopPlaceBoostConfiguration;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.rutebanken.netex.model.BusSubmodeEnumeration;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.NameTypeEnumeration;
import org.rutebanken.netex.model.StopPlace;
import org.rutebanken.netex.model.StopTypeEnumeration;
import org.rutebanken.netex.model.VehicleModeEnumeration;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class StopPlaceToPeliasMapper extends AbstractNetexPlaceToPeliasDocumentMapper<StopPlace> {

    // Using substitute layer for stops to avoid having to fork pelias (custom layers not configurable).
    public static final String STOP_PLACE_LAYER = "venue";

    private static final String KEY_IS_PARENT_STOP_PLACE = "IS_PARENT_STOP_PLACE";

    public static final String SOURCE_PARENT_STOP_PLACE = "openstreetmap";
    public static final String SOURCE_CHILD_STOP_PLACE = "geonames";

    private StopPlaceBoostConfiguration boostConfiguration;

    public StopPlaceToPeliasMapper(String participantRef, StopPlaceBoostConfiguration boostConfiguration) {
        super(participantRef);
        this.boostConfiguration = boostConfiguration;
    }

    @Override
    protected boolean isValid(StopPlace place) {
        // Ignore rail replacement bus
        if (VehicleModeEnumeration.BUS.equals(place.getTransportMode()) && BusSubmodeEnumeration.RAIL_REPLACEMENT_BUS.equals(place.getBusSubmode())) {
            return false;
        }

        // Skip stops without quays, unless they are parent stops
        if (isQuayLessNonParentStop(place)) {
            return false;
        }

        return super.isValid(place);
    }

    @Override
    protected List<MultilingualString> getNames(PlaceHierarchy<StopPlace> placeHierarchy) {
        List<MultilingualString> names = new ArrayList<>();

        collectNames(placeHierarchy, names, true);
        collectNames(placeHierarchy, names, false);

        return names.stream().filter(distinctByKey(name -> name.getValue())).collect(Collectors.toList());
    }

    private void collectNames(PlaceHierarchy<StopPlace> placeHierarchy, List<MultilingualString> names, boolean up) {
        StopPlace place = placeHierarchy.getPlace();
        if (place.getName() != null) {
            names.add(placeHierarchy.getPlace().getName());
        }

        if (place.getAlternativeNames() != null && !CollectionUtils.isEmpty(place.getAlternativeNames().getAlternativeName())) {
            place.getAlternativeNames().getAlternativeName().stream().filter(an -> an.getName() != null && an.getName().getLang() != null).forEach(n -> names.add(n.getName()));
        }

        if (up) {
            if (placeHierarchy.getParent() != null) {
                collectNames(placeHierarchy.getParent(), names, up);
            }
        } else {
            if (!CollectionUtils.isEmpty(placeHierarchy.getChildren())) {
                placeHierarchy.getChildren().forEach(child -> collectNames(child, names, up));
            }
        }
    }


    @Override
    protected void populateDocument(PlaceHierarchy<StopPlace> placeHierarchy, PeliasDocument document) {
        StopPlace place = placeHierarchy.getPlace();
        document.setSource(getSource(placeHierarchy));

        List<Pair<StopTypeEnumeration, Enum>> stopTypeAndSubModeList = aggregateStopTypeAndSubMode(placeHierarchy);

        document.setCategory(stopTypeAndSubModeList.stream().map(pair -> pair.getLeft()).filter(type -> type != null).map(type -> type.value()).collect(Collectors.toList()));

        if (place.getAlternativeNames() != null && !CollectionUtils.isEmpty(place.getAlternativeNames().getAlternativeName())) {
            place.getAlternativeNames().getAlternativeName().stream().filter(an -> NameTypeEnumeration.TRANSLATION.equals(an.getNameType()) && an.getName() != null && an.getName().getLang() != null).forEach(n -> document.addName(n.getName().getLang(), n.getName().getValue()));
        }

        // Make stop place rank highest in autocomplete by setting popularity
        long popularity = boostConfiguration.getPopularity(stopTypeAndSubModeList, place.getWeighting());
        document.setPopularity(popularity);

        if (place.getTariffZones() != null && place.getTariffZones().getTariffZoneRef() != null) {
            document.setTariffZones(place.getTariffZones().getTariffZoneRef().stream().map(zoneRef -> zoneRef.getRef()).collect(Collectors.toList()));
        }
    }

    /**
     * Categorize multimodal stops with separate sources in ordre to be able to filter in queries.
     * <p>
     * Multimodal parents with one source
     * Multimodal children with another source
     * Non-multimodal stops with default soure
     *
     * @param hierarchy
     * @return
     */
    private String getSource(PlaceHierarchy<StopPlace> hierarchy) {
        if (hierarchy.getParent() != null) {
            return SOURCE_CHILD_STOP_PLACE;
        } else if (!CollectionUtils.isEmpty(hierarchy.getChildren())) {
            return SOURCE_PARENT_STOP_PLACE;
        }
        return PeliasDocument.DEFAULT_SOURCE;
    }

    @Override
    protected String getLayer(StopPlace place) {
        return STOP_PLACE_LAYER;
    }


    private List<Pair<StopTypeEnumeration, Enum>> aggregateStopTypeAndSubMode(PlaceHierarchy<StopPlace> placeHierarchy) {
        List<Pair<StopTypeEnumeration, Enum>> types = new ArrayList<>();

        StopPlace stopPlace = placeHierarchy.getPlace();

        types.add(new ImmutablePair<>(stopPlace.getStopPlaceType(), getStopSubMode(stopPlace)));

        if (!CollectionUtils.isEmpty(placeHierarchy.getChildren())) {
            types.addAll(placeHierarchy.getChildren().stream().map(child -> aggregateStopTypeAndSubMode(child)).flatMap(typesForChild -> typesForChild.stream()).collect(Collectors.toList()));
        }

        return types;
    }


    private Enum getStopSubMode(StopPlace stopPlace) {

        if (stopPlace.getStopPlaceType() != null) {
            switch (stopPlace.getStopPlaceType()) {
                case AIRPORT:
                    return stopPlace.getAirSubmode();
                case HARBOUR_PORT:
                case FERRY_STOP:
                case FERRY_PORT:
                    return stopPlace.getWaterSubmode();
                case BUS_STATION:
                case COACH_STATION:
                case ONSTREET_BUS:
                    return stopPlace.getBusSubmode();
                case RAIL_STATION:
                    return stopPlace.getRailSubmode();
                case METRO_STATION:
                    return stopPlace.getMetroSubmode();
                case ONSTREET_TRAM:
                case TRAM_STATION:
                    return stopPlace.getTramSubmode();
            }
        }
        return null;
    }

    private boolean isQuayLessNonParentStop(StopPlace place) {
        if (place.getQuays() == null || CollectionUtils.isEmpty(place.getQuays().getQuayRefOrQuay())) {
            return place.getKeyList() == null || place.getKeyList().getKeyValue().stream().noneMatch(kv -> KEY_IS_PARENT_STOP_PLACE.equals(kv.getKey()) && Boolean.TRUE.toString().equalsIgnoreCase(kv.getValue()));
        }
        return false;
    }
}
