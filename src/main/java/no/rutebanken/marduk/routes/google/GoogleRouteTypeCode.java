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

package no.rutebanken.marduk.routes.google;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Google Transit does not (yet) support all Extended Route Type codes :https://developers.google.com/transit/gtfs/reference/extended-route-types
 * <p>
 * This class contains supported codes and mapping for unsupported codes.
 */
public enum GoogleRouteTypeCode {

    // Basic code set (supported by google)
    Tram(0),
    Subway(1),
    Rail(2),
    Bus(3),
    Ferry(4),
    Cable(5),
    Gondola(6),
    Funicular(7),

    // Extended values supported by google
    Railway_Service(100),
    High_Speed_Rail_Service(101),
    Long_Distance_Trains(102),
    Inter_Regional_Rail_Service(103),
    Car_Transport_Rail_Service(104),
    Sleeper_Rail_Service(105),
    Regional_Rail_Service(106),
    Tourist_Railway_Service(107),
    Rail_Shuttle_Within_Complex(108),
    Suburban_Railway(109),
    Coach_Service(200),
    International_Coach_Service(201),
    National_Coach_Service(202),
    Regional_Coach_Service(204),
    Commuter_Coach_Service(208),
    Urban_Railway_Service(400),
    Metro_Service(401),
    Underground_Service(402),
    Monorail(405),
    Bus_Service(700),
    Regional_Bus_Service(701),
    Express_Bus_Service(702),
    Local_Bus_Service(704),
    Trolleybus_Service(800),
    Tram_Service(900),
    Water_Transport_Service(1000),
    Telecabin_Service(1300),
    Funicular_Service(1400),
    Communal_Taxi_Service(1501),
    Miscellaneous_Service(1700),
    Cable_Car(1701),
    Horsedrawn_Carriage(1702),


    // Values not supported by google, with explicit mapping
    Suburban_Railway_Service(300, Railway_Service),
    Metro_Service2(500, Metro_Service),
    Underground_Service2(600, Underground_Service),
    Ferry_Service(1200, Water_Transport_Service),
    Taxi_Service(1500, Communal_Taxi_Service);

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleRouteTypeCode.class);

    private int code;
    private GoogleRouteTypeCode mapsTo;


    // Default to misc
    private static final GoogleRouteTypeCode FALLBACK_CODE = Miscellaneous_Service;

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
            return orgMapping.getCode();
        }

        // Reduce to base type (round to whole hundred) and try again
        if (org >= 100) {
            int baseType = org - (org % 100);
            GoogleRouteTypeCode baseTypeMapping = map(baseType);
            if (baseTypeMapping != null) {
                return baseTypeMapping.getCode();
            }
        }

        LOGGER.warn("Unable to map RouteType: {} to google supported value, using {} as default", org, FALLBACK_CODE);

        return FALLBACK_CODE.getCode();
    }

    private static GoogleRouteTypeCode map(int org) {
        GoogleRouteTypeCode match = fromCode(org);

        if (match != null) {
            if (match.getMapsTo() != null) {
                return match.getMapsTo();
            }

            return match;
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
        return Arrays.stream(GoogleRouteTypeCode.values()).filter(routeType -> routeType.getCode() == org).findFirst().orElse(null);
    }

}
