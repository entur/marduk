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


import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.rest.ProviderAndJobs;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

class ProviderAndJobsMapperTest {

	@Test
	void testMapJobResponseToProviderAndJobs() {
		List<Provider> providers = Arrays.asList(provider(1, "ref1"), provider(2, "ref2"), provider(3, "ref3"));

		JobResponse[] jobs = new JobResponse[]{job("ref1"), job("ref2"), job("ref1")};

		List<ProviderAndJobs> mapped = new ProviderAndJobsMapper().mapJobResponsesToProviderAndJobs(jobs, providers);
		assertEquals(3,mapped.size() );
		assertEquals(2,get(mapped,1).getNumJobs());
		assertEquals(1,get(mapped,2).getNumJobs());
		assertEquals(0,get(mapped,3).getNumJobs());
	}

	private ProviderAndJobs get(List<ProviderAndJobs> providerAndJobsList, int id) {
		for (ProviderAndJobs providerAndJobs : providerAndJobsList) {
			if (providerAndJobs.getProviderId() == id) {
				return providerAndJobs;
			}
		}
		return null;
	}

	private JobResponse job(String ref) {
		JobResponse job = new JobResponse();
		job.referential = ref;
		return job;
	}

	private Provider provider(long id, String ref) {
		Provider provider = new Provider();
		provider.id = id;
		provider.chouetteInfo = new ChouetteInfo();
		provider.chouetteInfo.referential = ref;
		return provider;
	}
}


