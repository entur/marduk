package no.rutebanken.marduk.routes.chouette.mapping;


import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.rest.ProviderAndJobs;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;

import java.util.*;

public class ProviderAndJobsMapper {

	public List<ProviderAndJobs> mapJobResponsesToProviderAndJobs(JobResponse[] jobs, Collection<Provider> providers) {
		Map<String, List<JobResponse>> jobsPerProvider = new HashMap<>();

		for (JobResponse jobResponse : jobs) {
			List<JobResponse> jobsForProvider = jobsPerProvider.get(jobResponse.referential);
			if (jobsForProvider == null) {
				jobsForProvider = new ArrayList<>();
				jobsPerProvider.put(jobResponse.referential, jobsForProvider);
			}
			jobsForProvider.add(jobResponse);
		}

		List<ProviderAndJobs> providerAndJobsList = new ArrayList<>();
		for (Provider provider : providers) {
			String referential = provider.getChouetteInfo().referential;
			providerAndJobsList.add(new ProviderAndJobs(provider.getId(), jobsPerProvider.get(referential)));
		}
		return providerAndJobsList;
	}
}
