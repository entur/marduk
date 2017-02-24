package no.rutebanken.marduk.geocoder.routes.control;


import org.junit.Assert;
import org.junit.Test;

import java.util.*;

public class GeoCoderTaskTest {

	@Test
	public void testSortingByStartedTasksAndThenEarliestPhase() {

		GeoCoderTask phase1 = new GeoCoderTask(GeoCoderTask.Phase.DOWNLOAD_SOURCE_DATA, 0, "s1");
		GeoCoderTask phase1OtherTarget = new GeoCoderTask(GeoCoderTask.Phase.DOWNLOAD_SOURCE_DATA, 0, "s1Other");
		GeoCoderTask phase2 = new GeoCoderTask(GeoCoderTask.Phase.TIAMAT_UPDATE, 0, "s2");
		GeoCoderTask startedTask = new GeoCoderTask(GeoCoderTask.Phase.TIAMAT_UPDATE, 2, "s2");
		GeoCoderTask phase4 = new GeoCoderTask(GeoCoderTask.Phase.PELIAS_UPDATE, 0, "s4");

		List<GeoCoderTask> expectedOrder = Arrays.asList(startedTask, phase1, phase1OtherTarget, phase2, phase4);
		Iterator<GeoCoderTask> itrExpected = expectedOrder.iterator();

		SortedSet<GeoCoderTask> sorted = new TreeSet<>(expectedOrder);

		sorted.forEach(s -> Assert.assertEquals(itrExpected.next(), s));

		Assert.assertFalse("Duplicates should not be allowed", sorted.add(new GeoCoderTask(GeoCoderTask.Phase.DOWNLOAD_SOURCE_DATA, 0, "s1")));
	}
}
