package no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Action meta data for ElastisearchCommands.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActionMetaData {

	@JsonProperty("_index")
	private String index;
	@JsonProperty("_type")
	private String type;
	@JsonProperty("_id")
	private String id;

	public ActionMetaData(String index, String type, String id) {
		this.index = index;
		this.type = type;
		this.id = id;
	}

	public ActionMetaData() {
	}

	public String getIndex() {
		return index;
	}

	public void setIndex(String index) {
		this.index = index;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
}
