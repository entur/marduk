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

package no.rutebanken.marduk.rest;

import no.rutebanken.marduk.routes.chouette.json.JobResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ProviderAndJobs {
	private final Long providerId;

	private final List<JobResponse> pendingJobs=new ArrayList<>();

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
