package no.rutebanken.marduk.geocoder.routes.pelias.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("geo_point")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeoPoint {
	@JsonProperty("lat")
	private Double lat;

	@JsonProperty("lon")
	private Double lon;


	public GeoPoint(Double lat, Double lon) {
		this.lat = lat;
		this.lon = lon;
	}

	public GeoPoint() {
	}

	public Double getLat() {
		return lat;
	}

	public void setLat(Double lat) {
		this.lat = lat;
	}

	public Double getLon() {
		return lon;
	}

	public void setLon(Double lon) {
		this.lon = lon;
	}
}
