/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.routes.chouette.mapping;


import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.rest.ProviderAndJobs;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
