package no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex;


import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import org.apache.commons.collections.CollectionUtils;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.TopographicPlace;

import java.util.Arrays;
import java.util.List;

import static org.rutebanken.netex.model.TopographicPlaceTypeEnumeration.PLACE_OF_INTEREST;

public class TopographicPlaceToPeliasMapper extends AbstractNetexPlaceToPeliasDocumentMapper<TopographicPlace> {

    private long popularity;

    private List<String> typeFilter;

    public TopographicPlaceToPeliasMapper(String participantRef, long popularity, List<String> typeFilter) {
        super(participantRef);
        this.popularity = popularity;
        this.typeFilter = typeFilter;
    }

    @Override
    protected void populateDocument(PlaceHierarchy<TopographicPlace> placeHierarchy, PeliasDocument document) {
        TopographicPlace place = placeHierarchy.getPlace();
        if (place.getAlternativeDescriptors() != null && !CollectionUtils.isEmpty(place.getAlternativeDescriptors().getTopographicPlaceDescriptor())) {
            place.getAlternativeDescriptors().getTopographicPlaceDescriptor().stream().filter(an -> an.getName() != null && an.getName().getLang() != null).forEach(n -> document.addName(n.getName().getLang(), n.getName().getValue()));
        }

        document.setPopularity(popularity);

        if (PLACE_OF_INTEREST.equals(place.getTopographicPlaceType())) {
            document.setCategory(Arrays.asList("poi"));
        }

        // Use descriptor.name if name is not set
        if (document.getDefaultName() == null && place.getDescriptor() != null) {
            MultilingualString descriptorName = place.getDescriptor().getName();
            document.setDefaultNameAndPhrase(descriptorName.getValue());
            if (descriptorName.getLang() != null) {
                document.addName(descriptorName.getLang(), descriptorName.getValue());
            }
        }
    }

    @Override
    protected boolean isValid(TopographicPlace place) {
        return isFilterMatch(place) && super.isValid(place);
    }

    private boolean isFilterMatch(TopographicPlace place) {
        if (CollectionUtils.isEmpty(typeFilter)) {
            return true;
        }
        if (place.getKeyList() == null || place.getKeyList().getKeyValue() == null) {
            return false;
        }

        return typeFilter.stream().anyMatch(filter -> place.getKeyList().getKeyValue().stream().map(key -> key.getKey() + "=" + key.getValue()).anyMatch(tag -> filter.startsWith(tag)));
    }

    @Override
    protected String getLayer(TopographicPlace place) {
        switch (place.getTopographicPlaceType()) {

            case PLACE_OF_INTEREST:
                return "address";

            // Still using adm units directly from kartverket. Change if tiamat IDs are needed.
//            case TOWN:
//                return "locality";
//
//            case COUNTY:
//                return "county";
//
//            case AREA:
//                return "borough";


        }

        return null;

    }


}
