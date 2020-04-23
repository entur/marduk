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

/**
 * RouteTypes supported in the  basic GTFS specification.
 */
public enum BasicRouteTypeCode {

    TRAM(0), SUBWAY(1), RAIL(2), BUS(3), FERRY(4), CABLE_CAR(5), GONDOLA(6), FUNICULAR(7);

    private static final Logger LOGGER = LoggerFactory.getLogger(BasicRouteTypeCode.class);

    private int code;

    BasicRouteTypeCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }


    public static int convertRouteType(int extendedType) {
        if (extendedType < 0) {
            return extendedType; // Probably not set
        }
        if (extendedType >= 0 && extendedType <= 7) {
            return extendedType; // Is actually basic type
        }
        if (extendedType >= 100 && extendedType < 200) { // Railway Service
            return BasicRouteTypeCode.RAIL.getCode();
        } else if (extendedType >= 200 && extendedType < 300) { //Coach Service
            return BasicRouteTypeCode.BUS.getCode();
        } else if (extendedType >= 300
                && extendedType < 500) { //Suburban Railway Service and Urban Railway service
            if (extendedType >= 401 && extendedType <= 402) {
                return BasicRouteTypeCode.SUBWAY.getCode();
            }
            return BasicRouteTypeCode.RAIL.getCode();
        } else if (extendedType >= 500 && extendedType < 700) {
            return BasicRouteTypeCode.SUBWAY.getCode();
        } else if (extendedType >= 700 && extendedType < 900) {
            return BasicRouteTypeCode.BUS.getCode();
        } else if (extendedType >= 900 && extendedType < 1000) {
            return BasicRouteTypeCode.TRAM.getCode();
        } else if (extendedType >= 1000 && extendedType < 1100) {
            return BasicRouteTypeCode.FERRY.getCode();
        } else if (extendedType >= 1200 && extendedType < 1300) {
            return BasicRouteTypeCode.FERRY.getCode();
        } else if (extendedType >= 1300 && extendedType < 1400) {
            return BasicRouteTypeCode.GONDOLA.getCode();
        } else if (extendedType >= 1400 && extendedType < 1500) {
            return BasicRouteTypeCode.FUNICULAR.getCode();
        }

        LOGGER.warn("Attempted to map unsupported extend route type to basic GTFS route type: {}. Using BUS as default. ", extendedType);
        return BasicRouteTypeCode.BUS.getCode();
    }

}
