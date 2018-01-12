package no.rutebanken.marduk.geocoder.sosi;

import no.vegvesen.nvdb.sosi.document.SosiElement;

public class SosiLocality extends SosiElementWrapper {

    public static final String OBJECT_TYPE = "Kommune";

    public SosiLocality(SosiElement sosiElement, SosiCoordinates coordinates) {
        super(sosiElement, coordinates);
    }

    @Override
    public Type getType() {
        return Type.LOCALITY;
    }

    @Override
    public String getId() {
        return pad(getProperty("KOMMUNENUMMER"), 4);
    }

    @Override
    protected String getNamePropertyName() {
        return "KOMMUNENAVN";
    }

    @Override
    public String getParentId() {
        return getId().substring(0, 2);
    }
}
