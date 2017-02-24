package no.rutebanken.marduk.geocoder.routes.control;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeoCoderTask implements Comparable<GeoCoderTask> {

	public enum Phase {DOWNLOAD_SOURCE_DATA, TIAMAT_UPDATE, TIAMAT_EXPORT, PELIAS_UPDATE, COMPLETE}

	private Phase phase;

	private int subStep;

	private String endpoint;

	private Date createdDate;

	private Map<String, Object> headers = new HashMap<>();

	public GeoCoderTask(Phase phase, int subStep, String endpoint) {
		this.phase = phase;
		this.subStep = subStep;
		this.endpoint = endpoint;
		createdDate = new Date();
	}

	public GeoCoderTask(Phase phase, String endpoint) {
		this(phase, 0, endpoint);
	}

	private GeoCoderTask() {
	}

	@Override
	public int compareTo(GeoCoderTask o) {

		// Phase in progress should be sorted first (should never be more than one)
		int subStepCmp = o.subStep - subStep;
		if (subStepCmp != 0) {
			return subStepCmp;
		}


		// Sort by phase, earliest first
		int stepCmp = phase.compareTo(o.phase);
		if (stepCmp != 0) {
			return stepCmp;
		}


		return endpoint.compareTo(o.endpoint);
	}

	@JsonIgnore
	public boolean isComplete() {
		return phase == Phase.COMPLETE;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		GeoCoderTask that = (GeoCoderTask) o;

		if (subStep != that.subStep) return false;
		if (phase != that.phase) return false;
		return endpoint.equals(that.endpoint);
	}

	@Override
	public int hashCode() {
		int result = phase.hashCode();
		result = 31 * result + subStep;
		result = 31 * result + endpoint.hashCode();
		return result;
	}

	public Phase getPhase() {
		return phase;
	}

	public void setPhase(Phase phase) {
		this.phase = phase;
	}

	public int getSubStep() {
		return subStep;
	}

	public void setSubStep(int subStep) {
		this.subStep = subStep;
	}

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public Date getCreatedDate() {
		return createdDate;
	}

	public void setCreatedDate(Date createdDate) {
		this.createdDate = createdDate;
	}

	public Map<String, Object> getHeaders() {
		return headers;
	}

	public void setHeaders(Map<String, Object> headers) {
		this.headers = headers;
	}

	@Override
	public String toString() {
		return "GeoCoderTask{" +
				       "phase=" + phase +
				       ", subStep=" + subStep +
				       ", endpoint='" + endpoint + '\'' +
				       ", createdDate=" + createdDate +
				       '}';
	}


}
