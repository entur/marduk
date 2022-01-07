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

package no.rutebanken.marduk.gtfs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Objects;

/**
 * Google Transit does not (yet) support all Extended Route Type codes :https://developers.google.com/transit/gtfs/reference/extended-route-types
 * <p>
 * This class contains supported codes and mapping for unsupported codes.
 */
public enum GoogleRouteTypeCode {

    // Basic code set (supported by google)
    TRAM(0),
    SUBWAY(1),
    RAIL(2),
    BUS(3),
    FERRY(4),
    CABLE(5),
    GONDOLA(6),
    FUNICULAR(7),

    // Extended values supported by google
    RAILWAY_SERVICE(100),
    HIGH_SPEED_RAIL_SERVICE(101),
    LONG_DISTANCE_TRAINS(102),
    INTER_REGIONAL_RAIL_SERVICE(103),
    CAR_TRANSPORT_RAIL_SERVICE(104),
    SLEEPER_RAIL_SERVICE(105),
    REGIONAL_RAIL_SERVICE(106),
    TOURIST_RAILWAY_SERVICE(107),
    RAIL_SHUTTLE_WITHIN_COMPLEX(108),
    SUBURBAN_RAILWAY(109),
    COACH_SERVICE(200),
    INTERNATIONAL_COACH_SERVICE(201),
    NATIONAL_COACH_SERVICE(202),
    REGIONAL_COACH_SERVICE(204),
    COMMUTER_COACH_SERVICE(208),
    URBAN_RAILWAY_SERVICE(400),
    METRO_SERVICE(401),
    UNDERGROUND_SERVICE(402),
    MONORAIL(405),
    BUS_SERVICE(700),
    REGIONAL_BUS_SERVICE(701),
    EXPRESS_BUS_SERVICE(702),
    LOCAL_BUS_SERVICE(704),
    TROLLEYBUS_SERVICE(800),
    TRAM_SERVICE(900),
    WATER_TRANSPORT_SERVICE(1000),
    TELECABIN_SERVICE(1300),
    FUNICULAR_SERVICE(1400),
    COMMUNAL_TAXI_SERVICE(1501),
    MISCELLANEOUS_SERVICE(1700),
    CABLE_CAR(1701),
    HORSEDRAWN_CARRIAGE(1702),


    // Values not supported by google, with explicit mapping
    SUBURBAN_RAILWAY_SERVICE(300, RAILWAY_SERVICE),
    METRO_SERVICE_2(500, METRO_SERVICE),
    UNDERGROUND_SERVICE_2(600, UNDERGROUND_SERVICE),
    FERRY_SERVICE(1200, WATER_TRANSPORT_SERVICE),
    TAXI_SERVICE(1500, COMMUNAL_TAXI_SERVICE);

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleRouteTypeCode.class);

    private final int code;
    private GoogleRouteTypeCode mapsTo;


    // Default to misc
    private static final GoogleRouteTypeCode FALLBACK_CODE = MISCELLANEOUS_SERVICE;

    GoogleRouteTypeCode(int code) {
        this.code = code;
    }

    GoogleRouteTypeCode(int code, GoogleRouteTypeCode mapsTo) {
        this(code);
        this.mapsTo = mapsTo;
    }


    public static int toGoogleSupportedRouteTypeCode(int org) {

        GoogleRouteTypeCode orgMapping = map(org);
        if (orgMapping != null) {
            return orgMapping.code;
        }

        // Reduce to base type (round to whole hundred) and try again
        if (org >= 100) {
            int baseType = org - (org % 100);
            GoogleRouteTypeCode baseTypeMapping = map(baseType);
            if (baseTypeMapping != null) {
                return baseTypeMapping.code;
            }
        }

        LOGGER.warn("Unable to map RouteType: {} to google supported value, using {} as default", org, FALLBACK_CODE);

        return FALLBACK_CODE.code;
    }

    private static GoogleRouteTypeCode map(int org) {
        GoogleRouteTypeCode match = fromCode(org);

        if (match != null) {
            return Objects.requireNonNullElse(match.mapsTo, match);

        }
        return null;
    }


    public int getCode() {
        return code;
    }


    public GoogleRouteTypeCode getMapsTo() {
        return mapsTo;
    }

    public static GoogleRouteTypeCode fromCode(int org) {
        return Arrays.stream(GoogleRouteTypeCode.values()).filter(routeType -> routeType.code == org).findFirst().orElse(null);
    }

}
