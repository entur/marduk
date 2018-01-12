package no.rutebanken.marduk.geocoder.routes.pelias.mapper.kartverket;


import no.rutebanken.marduk.geocoder.netex.TopographicPlaceAdapter;
import org.apache.commons.lang3.StringUtils;

public class PlaceToPeliasDocument extends TopographicPlaceAdapterToPeliasDocument {

    private long popularity;

    public PlaceToPeliasDocument(TopographicPlaceAdapter simpleFeature, long popularity) {
        super(simpleFeature);
        this.popularity = popularity;
    }

    @Override
    protected Long getPopularity() {
        return popularity;
    }

    @Override
    protected String getLayer() {
        return "address";
    }

    @Override
    protected String getLocalityId() {
        return feature.getParentId();
    }

    @Override
    protected String getCountyId() {
        return StringUtils.substring(getLocalityId(), 0, 2);
    }


}


