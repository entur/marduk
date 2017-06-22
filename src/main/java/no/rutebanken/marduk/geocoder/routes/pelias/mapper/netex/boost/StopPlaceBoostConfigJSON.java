package no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex.boost;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StopPlaceBoostConfigJSON {

    public long defaultValue;

    public Map<String,Double> interchangeFactors;

    public Map<String, Map<String,Double>> stopTypeFactors;
}
