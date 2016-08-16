package no.rutebanken.marduk.rest;

import no.rutebanken.marduk.routes.chouette.json.JobResponse;

public class ProviderAndJobs {
	private Long providerId;

	private int numJobs = 0;

	private JobResponse[] pendingJobs;

	public ProviderAndJobs(Long id, JobResponse[] pendingJobs) {
		super();
		this.providerId = id;
		this.pendingJobs = pendingJobs;
		if (pendingJobs != null) {
			this.numJobs = pendingJobs.length;
		}
	}

	public JobResponse[] getPendingJobs() {
		return pendingJobs;
	}

	public Long getProviderId() {
		return providerId;
	}

	public int getNumJobs() {
		return numJobs;
	}

}
