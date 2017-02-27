package no.rutebanken.marduk.geocoder.routes.pelias.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.geojson.Polygon;


import java.util.List;

/**
 * Document model stored in elasticsearch for Pelias
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PeliasDocument {

	@JsonProperty("source")
	private String source;

	@JsonProperty("layer")
	private String layer;

	@JsonProperty("source_id")
	private String sourceId;

	@JsonProperty("alpha3")
	private String alpha3;

	@JsonProperty("name")
	private Name name;

	@JsonProperty("phrase")
	private Name phrase;

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

	private PeliasDocument() {
	}

	public PeliasDocument(String layer, String source, String sourceId) {
		this.source = source;
		this.layer = layer;
		this.sourceId = sourceId;
	}

	public String getAlpha3() {
		return alpha3;
	}


	public void setAlpha3(String alpha3) {
		this.alpha3 = alpha3;
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

	public Name getName() {
		return name;
	}

	public void setName(Name name) {
		this.name = name;
	}

	public Name getPhrase() {
		return phrase;
	}

	public void setPhrase(Name phrase) {
		this.phrase = phrase;
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
