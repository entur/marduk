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

package no.rutebanken.marduk.geocoder.nabu.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.wololo.geojson.Polygon;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdministrativeZone {

    public enum AdministrativeZoneType {COUNTRY, COUNTY, LOCALITY, CUSTOM}

    public String codeSpace;

    public String privateCode;

    public String name;

    public String source;

    public Polygon polygon;

    public AdministrativeZoneType type;

    public AdministrativeZone(String codeSpace, String privateCode, String name, Polygon polygon, AdministrativeZoneType type, String source) {
        this.codeSpace = codeSpace;
        this.privateCode = privateCode;
        this.name = name;
        this.polygon = polygon;
        this.type = type;
        this.source = source;
    }

    public String getCodeSpace() {
        return codeSpace;
    }

    public String getPrivateCode() {
        return privateCode;
    }

    public String getName() {
        return name;
    }

    public Polygon getPolygon() {
        return polygon;
    }

    public AdministrativeZoneType getType() {
        return type;
    }

    public String getSource() {
        return source;
    }
}
