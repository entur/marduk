package no.rutebanken.marduk.geocoder.routes.pelias.babylon;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ScalingOrder {
	public String name;
	public String sender;
	@JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
	public Integer replicas;

	public ScalingOrder(String name, String sender, Integer replicas) {
		this.name = name;
		this.sender = sender;
		this.replicas = replicas;
	}
}
