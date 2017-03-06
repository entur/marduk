package no.rutebanken.marduk.geocoder.routes.pelias.babylon;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PodStatus {
	@JsonFormat(shape = JsonFormat.Shape.BOOLEAN)
	public Boolean present;
	@JsonFormat(shape = JsonFormat.Shape.BOOLEAN)
	public Boolean running;

	public String name;

	public Boolean getPresent() {
		return present;
	}

	public void setPresent(Boolean present) {
		this.present = present;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Boolean getRunning() {
		return running;
	}

	public void setRunning(Boolean running) {
		this.running = running;
	}
}
