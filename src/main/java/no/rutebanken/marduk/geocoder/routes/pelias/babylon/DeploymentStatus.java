package no.rutebanken.marduk.geocoder.routes.pelias.babylon;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@JsonIgnoreProperties(ignoreUnknown = true)
public class DeploymentStatus {
	@JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
	public Integer replicas;
	@JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
	public Integer availableReplicas;

	public Integer getReplicas() {
		return replicas;
	}

	public Integer getAvailableReplicas() {
		return availableReplicas;
	}
}
