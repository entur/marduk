package no.rutebanken.marduk.geocoder.nabu.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.wololo.geojson.Polygon;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdministrativeZone {

    public String codeSpace;

    public String privateCode;

    public String name;

    public Polygon polygon;

    public AdministrativeZone(String codeSpace, String privateCode, String name, Polygon polygon) {
        this.codeSpace = codeSpace;
        this.privateCode = privateCode;
        this.name = name;
        this.polygon = polygon;
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
}
