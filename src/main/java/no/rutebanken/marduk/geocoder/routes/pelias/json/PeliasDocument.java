package no.rutebanken.marduk.geocoder.routes.pelias.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.geojson.Polygon;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Document model stored in elasticsearch for Pelias
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PeliasDocument {


    public static final String DEFAULT_SOURCE = "whosonfirst";

    // Valid sources for querying: "osm,oa,gn,wof,openstreetmap,openaddresses,geonames,whosonfirst"
    @JsonProperty("source")
    private String source = DEFAULT_SOURCE;

    @JsonProperty("layer")
    private String layer;

    @JsonProperty("source_id")
    private String sourceId;

    @JsonProperty("name")
    private Map<String, String> nameMap;

    @JsonProperty("phrase")
    private Map<String, String> phraseMap;

    @JsonProperty("center_point")
    private GeoPoint centerPoint;

    @JsonProperty("shape")
    private Polygon shape;

    @JsonProperty("boudning_box")
    private String boundingBox;

    @JsonProperty("address_parts")
    private AddressParts addressParts;

    @JsonProperty("parent")
    private Parent parent;

    @JsonProperty("population")
    private Long population;

    @JsonProperty("popularity")
    private Long popularity;

    @JsonProperty("category")
    private List<String> category;

    @JsonProperty("tariff_zones")
    private List<String> tariffZones;

    private PeliasDocument() {
    }

    public PeliasDocument(String layer, String sourceId) {
        this.layer = layer;
        this.sourceId = sourceId;
    }


    public PeliasDocument(String layer, String source, String sourceId) {
        this.source = source;
        this.layer = layer;
        this.sourceId = sourceId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getLayer() {
        return layer;
    }

    public void setLayer(String layer) {
        this.layer = layer;
    }

    public Map<String, String> getNameMap() {
        return nameMap;
    }

    public void setNameMap(Map<String, String> nameMap) {
        this.nameMap = nameMap;
    }

    public void setDefaultNameAndPhrase(String name) {
        addName("default", name);
        addPhrase("default", name);
    }

    @JsonIgnore
    public String getDefaultName() {
        if (nameMap != null) {
            return nameMap.get("default");
        }
        return null;
    }

    public void addName(String language, String name) {
        if (nameMap == null) {
            nameMap = new HashMap<>();
        }
        nameMap.put(language, name);
    }


    @JsonIgnore
    public String getDefaultPhrase() {
        if (phraseMap != null) {
            return phraseMap.get("default");
        }
        return null;
    }

    public void addPhrase(String language, String phrase) {
        if (phraseMap == null) {
            phraseMap = new HashMap<>();
        }
        phraseMap.put(language, phrase);
    }

    public Map<String, String> getPhraseMap() {
        return phraseMap;
    }

    public void setPhraseMap(Map<String, String> phraseMap) {
        this.phraseMap = phraseMap;
    }

    public Polygon getShape() {
        return shape;
    }

    public void setShape(Polygon shape) {
        this.shape = shape;
    }

    public String getBoundingBox() {
        return boundingBox;
    }

    public void setBoundingBox(String boundingBox) {
        this.boundingBox = boundingBox;
    }

    public AddressParts getAddressParts() {
        return addressParts;
    }

    public void setAddressParts(AddressParts addressParts) {
        this.addressParts = addressParts;
    }

    public Parent getParent() {
        return parent;
    }

    public void setParent(Parent parent) {
        this.parent = parent;
    }

    public Long getPopulation() {
        return population;
    }

    public void setPopulation(Long population) {
        this.population = population;
    }

    public Long getPopularity() {
        return popularity;
    }

    public void setPopularity(Long popularity) {
        this.popularity = popularity;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public List<String> getCategory() {
        return category;
    }

    public void setCategory(List<String> category) {
        this.category = category;
    }

    public List<String> getTariffZones() {
        return tariffZones;
    }

    public void setTariffZones(List<String> tariffZones) {
        this.tariffZones = tariffZones;
    }

    public GeoPoint getCenterPoint() {
        return centerPoint;
    }

    public void setCenterPoint(GeoPoint centerPoint) {
        this.centerPoint = centerPoint;
    }

    @JsonIgnore
    public boolean isValid() {
        return source != null && sourceId != null && layer != null;
    }


}
