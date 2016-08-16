package no.rutebanken.marduk.routes.chouette.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JobResponse {

	public Integer id;
	public Integer getId() {
		return id;
	}
	
	public String referential;
	public String action;
	public String type;
	public Long created;
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public Long started;
	public Long updated;
	public Status status;

	public Status getStatus() {
		return status;
	}

}
