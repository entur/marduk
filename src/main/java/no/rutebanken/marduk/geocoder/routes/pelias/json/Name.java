package no.rutebanken.marduk.geocoder.routes.pelias.json;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Name {
	@JsonProperty("default")
	private String defaultName;
	@JsonProperty("alt")
	private String alternativeName;

	public Name(String defaultName, String alternativeName) {
		this.defaultName = defaultName;
		this.alternativeName = alternativeName;
	}

	public Name() {
	}

	public String getDefaultName() {
		return defaultName;
	}

	public void setDefaultName(String defaultName) {
		this.defaultName = defaultName;
	}

	public String getAlternativeName() {
		return alternativeName;
	}

	public void setAlternativeName(String alternativeName) {
		this.alternativeName = alternativeName;
	}
}
