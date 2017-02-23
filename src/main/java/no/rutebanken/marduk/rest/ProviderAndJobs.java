package no.rutebanken.marduk.rest;

import no.rutebanken.marduk.routes.chouette.json.JobResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ProviderAndJobs {
	private Long providerId;

	private List<JobResponse> pendingJobs=new ArrayList<>();

	public ProviderAndJobs(Long id, Collection<JobResponse> pendingJobs) {
		super();
		this.providerId = id;
		if (pendingJobs != null) {
			this.pendingJobs.addAll(pendingJobs);
		}
	}

	public List<JobResponse> getPendingJobs() {
		return pendingJobs;
	}

	public Long getProviderId() {
		return providerId;
	}

	public int getNumJobs() {
		return pendingJobs.size();
	}

}
