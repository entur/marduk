package no.rutebanken.marduk.geocoder.netex.pbf;


import org.junit.Assert;
import org.junit.Test;
import org.rutebanken.netex.model.IanaCountryTldEnumeration;
import org.rutebanken.netex.model.TopographicPlace;
import org.rutebanken.netex.model.TopographicPlaceTypeEnumeration;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class PbfTopographicPlaceReaderTest {

	@Test
	public void testParsePbfSampleFile() throws Exception {
		PbfTopographicPlaceReader reader =
				new PbfTopographicPlaceReader(Arrays.asList("leisure=common", "naptan:indicator"), IanaCountryTldEnumeration.NO,
						                             new File("src/test/resources/no/rutebanken/marduk/geocoder/pbf/sample.pbf"));

		BlockingQueue<TopographicPlace> queue = new LinkedBlockingDeque();
		reader.addToQueue(queue);

		Assert.assertEquals(4, queue.size());

		for (TopographicPlace tp : queue) {
			Assert.assertEquals(IanaCountryTldEnumeration.NO, tp.getCountryRef().getRef());
			Assert.assertEquals(TopographicPlaceTypeEnumeration.PLACE_OF_INTEREST, tp.getTopographicPlaceType());
			Assert.assertNotNull(tp.getName());
			Assert.assertNotNull(tp.getCentroid());
		}

	}
}
