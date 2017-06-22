package no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex.boost;

import org.junit.Assert;
import org.junit.Test;
import org.rutebanken.netex.model.AirSubmodeEnumeration;
import org.rutebanken.netex.model.BusSubmodeEnumeration;
import org.rutebanken.netex.model.InterchangeWeightingEnumeration;
import org.rutebanken.netex.model.RailSubmodeEnumeration;
import org.rutebanken.netex.model.StopTypeEnumeration;
import org.rutebanken.netex.model.WaterSubmodeEnumeration;

public class StopPlaceBoostConfigurationTest {

    StopPlaceBoostConfiguration boostConfiguration = new StopPlaceBoostConfiguration("{\"defaultValue\": 1000," +
                                                                                             "  \"stopTypeFactors\": {" +
                                                                                             "    \"metroStation\": {" +
                                                                                             "      \"*\": 2" +
                                                                                             "    }," +
                                                                                             "    \"busStation\": {" +
                                                                                             "      \"localBus\": 2" +
                                                                                             "    }," +
                                                                                             "    \"railStation\": {" +
                                                                                             "      \"*\": 2," +
                                                                                             "      \"highSpeedRail\": 6" +
                                                                                             "    }," +
                                                                                             "    \"airport\": {" +
                                                                                             "      \"*\": 2" +
                                                                                             "    }" +
                                                                                             "  }," +
                                                                                             "  \"interchangeFactors\": {" +
                                                                                             "    \"recommendedInterchange\": 3," +
                                                                                             "    \"preferredInterchange\": 10" +
                                                                                             "  }" +
                                                                                             "}");

    @Test
    public void emptyConfigShouldYieldPopularity0ForAllStops() {
        Assert.assertEquals(0, new StopPlaceBoostConfiguration("{}").getPopularity(StopTypeEnumeration.FERRY_STOP, null, InterchangeWeightingEnumeration.NO_INTERCHANGE));
        Assert.assertEquals(0, new StopPlaceBoostConfiguration("{}").getPopularity(StopTypeEnumeration.BUS_STATION, BusSubmodeEnumeration.REGIONAL_BUS, InterchangeWeightingEnumeration.PREFERRED_INTERCHANGE));
    }


    @Test
    public void allValuesMatchedShouldYieldMultipliedPopularity() {
        Assert.assertEquals(1000 * 6 * 10, boostConfiguration.getPopularity(StopTypeEnumeration.RAIL_STATION, RailSubmodeEnumeration.HIGH_SPEED_RAIL, InterchangeWeightingEnumeration.PREFERRED_INTERCHANGE));
    }

    @Test
    public void noValuesMatchedShouldYieldDefaultPopularity() {
        Assert.assertEquals(1000, boostConfiguration.getPopularity(StopTypeEnumeration.FERRY_STOP, WaterSubmodeEnumeration.AIRPORT_BOAT_LINK, InterchangeWeightingEnumeration.NO_INTERCHANGE));
    }

    @Test
    public void subModeNotFoundShouldYieldDefaultForStopType() {
        Assert.assertEquals(1000 * 2, boostConfiguration.getPopularity(StopTypeEnumeration.AIRPORT, AirSubmodeEnumeration.CANAL_BARGE, InterchangeWeightingEnumeration.NO_INTERCHANGE));
    }

    @Test
    public void subModeNotSetShouldYieldDefaultForStopType() {
        Assert.assertEquals(1000 * 2, boostConfiguration.getPopularity(StopTypeEnumeration.AIRPORT, null, null));
    }


    @Test
    public void subModeNotFoundAndNoStopTypeDefaultShouldYieldDefaultPopularity() {
        Assert.assertEquals(1000, boostConfiguration.getPopularity(StopTypeEnumeration.BUS_STATION, BusSubmodeEnumeration.REGIONAL_BUS, null));
    }

    @Test
    public void noValuesSetShouldYieldDefaultPopularity() {
        Assert.assertEquals(1000, boostConfiguration.getPopularity(null, null, null));
    }

}

