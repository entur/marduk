package no.rutebanken.marduk.routes.chouette.mapping;


import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.rest.ProviderAndJobs;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ProviderAndJobsMapperTest {

	@Test
	public void testMapJobResponseToProviderAndJobs() {
		List<Provider> providers = Arrays.asList(provider(1, "ref1"), provider(2, "ref2"), provider(3, "ref3"));

		JobResponse[] jobs = new JobResponse[]{job("ref1"), job("ref2"), job("ref1")};

		List<ProviderAndJobs> mapped = new ProviderAndJobsMapper().mapJobResponsesToProviderAndJobs(jobs, providers);
		Assert.assertEquals(3,mapped.size() );
		Assert.assertEquals(2,get(mapped,1).getNumJobs());
		Assert.assertEquals(1,get(mapped,2).getNumJobs());
		Assert.assertEquals(0,get(mapped,3).getNumJobs());
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


