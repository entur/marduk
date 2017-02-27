package no.rutebanken.marduk.geocoder.routes.pelias.mapper.kartverket;


import no.rutebanken.marduk.geocoder.routes.pelias.kartverket.KartverketAddress;
import org.junit.Assert;
import org.junit.Test;

public class KartverketAddressTest {


	@Test
	public void testFormatFylkesNo() {
		Assert.assertEquals("02", address("0203", null).getFylkesNo());
		Assert.assertEquals("02", address("203", null).getFylkesNo());
		Assert.assertNull(address(null, null).getFylkesNo());
	}

	@Test
	public void testFormatFullKommuneNo() {
		Assert.assertEquals("0203", address("0203", null).getFullKommuneNo());
		Assert.assertEquals("0203", address("203", null).getFullKommuneNo());
		Assert.assertNull(address(null, null).getFullKommuneNo());
	}

	@Test
	public void testFormatFullGrunnkretsNo() {
		Assert.assertEquals("02030560", address("0203", "0560").getFullGrunnkretsNo());
		Assert.assertEquals("02030560", address("203", "560").getFullGrunnkretsNo());
		Assert.assertNull(address(null, null).getFullGrunnkretsNo());
		Assert.assertNull(address("0203", null).getFullGrunnkretsNo());
		Assert.assertNull(address(null, "531").getFullGrunnkretsNo());
	}

	private KartverketAddress address(String kommunenr, String grunnkretsnr) {
		KartverketAddress address = new KartverketAddress();
		address.setGrunnkretsnr(grunnkretsnr);
		address.setKommunenr(kommunenr);
		return address;
	}
}
