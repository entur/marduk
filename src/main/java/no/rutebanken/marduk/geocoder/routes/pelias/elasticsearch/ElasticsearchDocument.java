package no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ElasticsearchDocument {
    @JsonProperty("_source")
    public PeliasDocument source;

    public PeliasDocument getSource() {
        return source;
    }
}
