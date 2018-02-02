/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex.boost;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Assert;
import org.junit.Test;
import org.rutebanken.netex.model.AirSubmodeEnumeration;
import org.rutebanken.netex.model.BusSubmodeEnumeration;
import org.rutebanken.netex.model.InterchangeWeightingEnumeration;
import org.rutebanken.netex.model.RailSubmodeEnumeration;
import org.rutebanken.netex.model.StopTypeEnumeration;
import org.rutebanken.netex.model.WaterSubmodeEnumeration;

import java.util.Arrays;

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
        Assert.assertEquals(0, new StopPlaceBoostConfiguration("{}").getPopularity(Arrays.asList(new ImmutablePair<>(StopTypeEnumeration.FERRY_STOP, null)), InterchangeWeightingEnumeration.NO_INTERCHANGE));
        Assert.assertEquals(0, new StopPlaceBoostConfiguration("{}").getPopularity(Arrays.asList(new ImmutablePair<>(StopTypeEnumeration.BUS_STATION, BusSubmodeEnumeration.REGIONAL_BUS)), InterchangeWeightingEnumeration.PREFERRED_INTERCHANGE));
    }


    @Test
    public void allValuesMatchedShouldYieldMultipliedPopularity() {
        Assert.assertEquals(1000 * 6 * 10, boostConfiguration.getPopularity(Arrays.asList(new ImmutablePair<>(StopTypeEnumeration.RAIL_STATION, RailSubmodeEnumeration.HIGH_SPEED_RAIL)), InterchangeWeightingEnumeration.PREFERRED_INTERCHANGE));
    }


    @Test
    public void multipleTypesAndSubModesShouldBeSummarized() {
        Assert.assertEquals(1000 * (6 + 2) * 10, boostConfiguration.getPopularity(Arrays.asList(new ImmutablePair<>(StopTypeEnumeration.RAIL_STATION, RailSubmodeEnumeration.HIGH_SPEED_RAIL), new ImmutablePair<>(StopTypeEnumeration.AIRPORT, null),
                new ImmutablePair<>(StopTypeEnumeration.FERRY_PORT, null)), InterchangeWeightingEnumeration.PREFERRED_INTERCHANGE));
    }

    @Test
    public void noValuesMatchedShouldYieldDefaultPopularity() {
        Assert.assertEquals(1000, boostConfiguration.getPopularity(Arrays.asList(new ImmutablePair<>(StopTypeEnumeration.FERRY_STOP, WaterSubmodeEnumeration.AIRPORT_BOAT_LINK)), InterchangeWeightingEnumeration.NO_INTERCHANGE));
    }

    @Test
    public void subModeNotFoundShouldYieldDefaultForStopType() {
        Assert.assertEquals(1000 * 2, boostConfiguration.getPopularity(Arrays.asList(new ImmutablePair<>(StopTypeEnumeration.AIRPORT, AirSubmodeEnumeration.CANAL_BARGE)), InterchangeWeightingEnumeration.NO_INTERCHANGE));
    }

    @Test
    public void subModeNotSetShouldYieldDefaultForStopType() {
        Assert.assertEquals(1000 * 2, boostConfiguration.getPopularity(Arrays.asList(new ImmutablePair<>(StopTypeEnumeration.AIRPORT, null)), null));
    }


    @Test
    public void subModeNotFoundAndNoStopTypeDefaultShouldYieldDefaultPopularity() {
        Assert.assertEquals(1000, boostConfiguration.getPopularity(Arrays.asList(new ImmutablePair<>(StopTypeEnumeration.BUS_STATION, BusSubmodeEnumeration.REGIONAL_BUS)), null));
    }

    @Test
    public void noValuesSetShouldYieldDefaultPopularity() {
        Assert.assertEquals(1000, boostConfiguration.getPopularity(Arrays.asList(new ImmutablePair<>(null, null)), null));
    }

}

