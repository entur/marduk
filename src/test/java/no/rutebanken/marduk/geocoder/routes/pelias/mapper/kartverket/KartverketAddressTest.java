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
