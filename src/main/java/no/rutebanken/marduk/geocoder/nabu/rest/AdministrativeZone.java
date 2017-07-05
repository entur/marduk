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
