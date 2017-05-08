package no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ElasticsearchHits {
    public int total;

    public List<ElasticsearchDocument> hits;


    public int getTotal() {
        return total;
    }

    public List<ElasticsearchDocument> getHits() {
        return hits;
    }

}
