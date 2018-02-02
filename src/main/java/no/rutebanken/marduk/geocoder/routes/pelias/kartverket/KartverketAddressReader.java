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

package no.rutebanken.marduk.geocoder.routes.pelias.kartverket;

import org.beanio.BeanReader;
import org.beanio.StreamFactory;
import org.beanio.builder.DelimitedParserBuilder;
import org.beanio.builder.StreamBuilder;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class KartverketAddressReader {


	public Collection<KartverketAddress> read(InputStream inputStream) {
		BeanReader in = getBeanReader(inputStream);

		List<KartverketAddress> addresses = asList(in);

		in.close();
		return addresses;
	}

	private BeanReader getBeanReader(InputStream inputStream) {
		StreamFactory factory = StreamFactory.newInstance();

		String streamName = "address";
		StreamBuilder builder = new StreamBuilder(streamName);
		builder.format("delimited");
		builder.parser(new DelimitedParserBuilder(';'));
		builder.readOnly();

		builder.addRecord(KartverketHeader.class);
		builder.addRecord(KartverketAddress.class);
		factory.define(builder);

		BufferedReader buffReader = new BufferedReader(new InputStreamReader(inputStream, Charset.forName("UTF-8")));

		return factory.createReader(streamName, buffReader);
	}

	private List<KartverketAddress> asList(BeanReader in) {
		List<KartverketAddress> addresses = new ArrayList<>();
		Object record;
		while ((record = in.read()) != null) {
			if (in.getLineNumber() > 1) {
				KartverketAddress address = (KartverketAddress) record;
				addresses.add(address);
			}

		}
		return addresses;
	}
}
