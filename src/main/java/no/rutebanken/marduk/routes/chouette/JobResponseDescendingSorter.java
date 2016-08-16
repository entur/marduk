package no.rutebanken.marduk.routes.chouette;

import java.util.Comparator;

import no.rutebanken.marduk.routes.chouette.json.JobResponse;

public class JobResponseDescendingSorter implements Comparator<JobResponse> {

	@Override
	public int compare(JobResponse o1, JobResponse o2) {
		return o2.id.compareTo(o1.id);
	}

}
