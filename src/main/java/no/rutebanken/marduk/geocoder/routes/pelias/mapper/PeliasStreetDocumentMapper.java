package no.rutebanken.marduk.geocoder.routes.pelias.mapper;

import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchDocument;
import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchResults;
import no.rutebanken.marduk.geocoder.routes.pelias.json.AddressParts;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PeliasStreetDocumentMapper {

    @Value("${pelias.street.name.default.popularity:100}")
    private long defaultPopularity;


    /**
     * Create an ElasticSearch index command for a new Pelias document representing a street based on all addresses on that street.
     * @param addressSearchResults
     * @return
     */
    public ElasticsearchCommand createStreetFromAddressDocuments(ElasticsearchResults addressSearchResults) {
        List<ElasticsearchDocument> addressDocuments = addressSearchResults.getHits().getHits();
        List<PeliasDocument> addressesInStreet = addressDocuments.stream().map(d -> d.getSource())
                                                         .filter(pd -> pd.getAddressParts() != null && pd.getAddressParts().getNumber() != null).collect(Collectors.toList());
        Collections.sort(addressesInStreet,
                (o1, o2) -> o1.getAddressParts().getNumber().compareTo(o2.getAddressParts().getNumber()));
        PeliasDocument medianAddress = addressesInStreet.get(addressesInStreet.size() / 2);

        String streetName = medianAddress.getAddressParts().getStreet();
        PeliasDocument street = new PeliasDocument("address", medianAddress.getSource(), streetName);

        street.setAlpha3(medianAddress.getAlpha3());
        street.setDefaultName(streetName);
        street.setParent(medianAddress.getParent());

        street.setCenterPoint(medianAddress.getCenterPoint());
        AddressParts addressParts = new AddressParts();
        addressParts.setName(streetName);
        addressParts.setStreet(streetName);
        street.setAddressParts(addressParts);


        street.setPopularity(defaultPopularity);

        return ElasticsearchCommand.peliasIndexCommand(street);
    }

}
