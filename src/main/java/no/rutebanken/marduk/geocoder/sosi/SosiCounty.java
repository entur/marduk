package no.rutebanken.marduk.geocoder.sosi;

import no.vegvesen.nvdb.sosi.document.SosiElement;

public class SosiCounty extends SosiElementWrapper {

    public static final String OBJECT_TYPE = "Fylke";

    public SosiCounty(SosiElement sosiElement, SosiCoordinates coordinates) {
        super(sosiElement, coordinates);
    }

    @Override
    public Type getType() {
        return Type.COUNTY;
    }

    @Override
    public String getId() {
        return pad(getProperty("FYLKESNUMMER"), 2);
    }

    @Override
    public String getIsoCode() {
        return "NO-" + getId();
    }

    @Override
    protected String getNamePropertyName() {
        return "FYLKESNAVN";
    }
}
