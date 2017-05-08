package no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ElasticsearchResults {


    public ElasticsearchHits hits;

    public ElasticsearchHits getHits() {
        return hits;
    }

}
