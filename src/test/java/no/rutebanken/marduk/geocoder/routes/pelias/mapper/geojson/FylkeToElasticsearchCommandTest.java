package no.rutebanken.marduk.geocoder.routes.pelias.mapper.geojson;


import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public class FylkeToElasticsearchCommandTest {

	@Test
	public void testTransform() throws Exception {
		KartverketGeoJsonStreamToElasticsearchCommands transformer = new KartverketGeoJsonStreamToElasticsearchCommands();
		Collection<ElasticsearchCommand> commands = transformer
				                                            .transform(new FileInputStream("src/test/resources/no/rutebanken/marduk/geocoder/geojson/fylker.geojson"));

		Assert.assertEquals(4, commands.size());

		commands.forEach(c -> assertCommand(c));

		PeliasDocument kalland = commands.stream().map(c -> (PeliasDocument) c.getSource()).filter(d -> "Buskerud".equals(d.getDefaultName())).collect(Collectors.toList()).get(0);
		assertBuskerud(kalland);
	}

	private void assertBuskerud(PeliasDocument buskerud) {
		Assert.assertNotNull(buskerud.getShape());
		Assert.assertEquals(Arrays.asList("NOR"), buskerud.getParent().getCountryId());
		Assert.assertEquals("06", buskerud.getSourceId());
	}

	private void assertCommand(ElasticsearchCommand command) {
		Assert.assertNotNull(command.getIndex());
		Assert.assertEquals("pelias", command.getIndex().getIndex());
		Assert.assertEquals("county", command.getIndex().getType());
	}

}
