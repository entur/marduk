package no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch;


import net.logstash.logback.encoder.org.apache.commons.lang.StringUtils;
import no.rutebanken.marduk.geocoder.routes.pelias.json.GeoPoint;
import no.rutebanken.marduk.geocoder.routes.pelias.json.Parent;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import org.junit.Assert;
import org.junit.Test;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class ElasticsearchBulkCommandWriterTest {


	@Test
	public void testWriteBulkCommandWithPeliasDocuments() throws Exception {
		List<ElasticsearchCommand> commands = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			commands.add(ElasticsearchCommand.peliasIndexCommand(doc("møre" + i)));

		}

		StringWriter writer = new StringWriter();
		new ElasticsearchBulkCommandWriter(writer).write(commands);
		String asString = writer.toString();

		Assert.assertEquals(commands.size() * 2, StringUtils.countMatches(asString, "\n"));
		Assert.assertTrue(asString.contains("\"name\":{\"default\":\"møre0\""));
	}


	private PeliasDocument doc(String name) {
		PeliasDocument peliasDocument = new PeliasDocument("layer","sourceId");
		peliasDocument.setDefaultNameAndPhrase(name);
		peliasDocument.setCenterPoint(new GeoPoint(51.7651177, -0.2336668));

		peliasDocument.setParent(Parent.builder().withBorough("bor").withCountryId("NOR").build());

		return peliasDocument;
	}

}
