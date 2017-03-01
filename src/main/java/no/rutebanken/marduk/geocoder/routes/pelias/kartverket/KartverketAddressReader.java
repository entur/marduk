package no.rutebanken.marduk.geocoder.routes.pelias.kartverket;

import org.beanio.BeanReader;
import org.beanio.StreamFactory;
import org.beanio.builder.DelimitedParserBuilder;
import org.beanio.builder.FixedLengthParserBuilder;
import org.beanio.builder.StreamBuilder;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;


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

		BufferedReader buffReader = new BufferedReader(new InputStreamReader(inputStream));

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
