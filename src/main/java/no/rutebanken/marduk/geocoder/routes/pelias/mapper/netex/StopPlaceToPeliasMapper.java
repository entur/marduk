package no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex;


import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex.boost.StopPlaceBoostConfiguration;
import org.apache.commons.collections.CollectionUtils;
import org.rutebanken.netex.model.BusSubmodeEnumeration;
import org.rutebanken.netex.model.StopPlace;
import org.rutebanken.netex.model.VehicleModeEnumeration;

import java.util.Arrays;

public class StopPlaceToPeliasMapper extends AbstractNetexPlaceToPeliasDocumentMapper<StopPlace> {

    // Using substitute layer for stops to avoid having to fork pelias (custom layers not configurable).
    public static final String STOP_PLACE_LAYER = "venue";

    private static final String KEY_IS_PARENT_STOP_PLACE = "IS_PARENT_STOP_PLACE";

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
        if (isQuayLessNonParentStop(place)){
            return false;
        }

        return super.isValid(place);
    }

    @Override
    protected void populateDocument(StopPlace place, PeliasDocument document) {
        if (place.getStopPlaceType() != null) {
            document.setCategory(Arrays.asList(place.getStopPlaceType().value()));
        }

        if (place.getAlternativeNames() != null && !CollectionUtils.isEmpty(place.getAlternativeNames().getAlternativeName())) {
            place.getAlternativeNames().getAlternativeName().stream().filter(an -> an.getName() != null && an.getName().getLang() != null).forEach(n -> document.addName(n.getName().getLang(), n.getName().getValue()));
        }

        // Make stop place rank highest in autocomplete by setting popularity
        long popularity = boostConfiguration.getPopularity(place.getStopPlaceType(), getStopSubMode(place), place.getWeighting());
        document.setPopularity(popularity);
    }

    @Override
    protected String getLayer(StopPlace place) {
        return STOP_PLACE_LAYER;
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
