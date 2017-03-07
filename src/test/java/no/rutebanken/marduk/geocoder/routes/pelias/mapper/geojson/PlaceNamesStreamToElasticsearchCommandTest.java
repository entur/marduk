package no.rutebanken.marduk.geocoder.routes.pelias.mapper.geojson;

import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public class PlaceNamesStreamToElasticsearchCommandTest {


	@Test
	public void testTransform() throws Exception {
		KartverketGeoJsonStreamToElasticsearchCommands transformer = new KartverketGeoJsonStreamToElasticsearchCommands();
		Collection<ElasticsearchCommand> commands = transformer
				                                            .transform(new FileInputStream("src/test/resources/no/rutebanken/marduk/geocoder/geojson/stedsnavn.geojson"));

		Assert.assertEquals(2, commands.size());

		commands.forEach(c -> assertCommand(c));

		PeliasDocument kalland = commands.stream().map(c -> (PeliasDocument) c.getSource()).filter(d -> "Kalland".equals(d.getDefaultName())).collect(Collectors.toList()).get(0);
		assertKalland(kalland);
	}

	private void assertKalland(PeliasDocument kalland) {
		Assert.assertEquals(Double.valueOf(58.088692), kalland.getCenterPoint().getLat());
		Assert.assertEquals(Double.valueOf(7.508508), kalland.getCenterPoint().getLon());

		Assert.assertEquals(Arrays.asList("NOR"),kalland.getParent().getCountryId());
		Assert.assertEquals(Arrays.asList("10"),kalland.getParent().getCountyId());
		Assert.assertEquals(Arrays.asList("1002"),kalland.getParent().getLocalityId());
	}

	private void assertCommand(ElasticsearchCommand command) {
		Assert.assertNotNull(command.getIndex());
		Assert.assertEquals("pelias", command.getIndex().getIndex());
		Assert.assertEquals("neighbourhood", command.getIndex().getType());
	}

}
