package no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex;


import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileInputStream;
import java.util.Collection;
import java.util.stream.Collectors;

public class DeliveryPublicationStreamToElasticsearchCommandsTest {

	@Test
	public void testTransform() throws Exception {
		Collection<ElasticsearchCommand> commands = new DeliveryPublicationStreamToElasticsearchCommands()
				                                            .transform(new FileInputStream("src/test/resources/no/rutebanken/marduk/geocoder/netex/tiamat-export.xml"));

		Assert.assertEquals(4, commands.size());
		commands.forEach(c -> assertCommand(c));


		assertKnownStopPlace(byId(commands, "NSR:StopPlace:39231"));
		assertKnownCounty(byId(commands, "NSR:TopographicPlace:1"));
		assertKnownTown(byId(commands, "NSR:TopographicPlace:3"));
	}

	private PeliasDocument byId(Collection<ElasticsearchCommand> commands, String id) {
		return commands.stream().map(c -> (PeliasDocument) c.getSource()).filter(d -> d.getSourceId().equals(id)).collect(Collectors.toList()).get(0);
	}


	private void assertKnownStopPlace(PeliasDocument known) throws Exception {
		Assert.assertEquals("Harstad/Narvik Lufthavn", known.getName().getDefaultName());
		Assert.assertEquals("STOP_PLACE", known.getLayer());
		Assert.assertEquals(68.490412, known.getCenterPoint().getLat(), 0.0001);
		Assert.assertEquals(16.687364, known.getCenterPoint().getLon(), 0.0001);

	}

	private void assertKnownTown(PeliasDocument known) throws Exception {
		Assert.assertEquals("Fredrikstad", known.getName().getDefaultName());
		Assert.assertEquals("TOWN", known.getLayer());
		Assert.assertEquals("NOR", known.getAlpha3());
	}

	private void assertKnownCounty(PeliasDocument known) throws Exception {
		Assert.assertEquals("Østfold", known.getName().getDefaultName());
		Assert.assertEquals("COUNTY", known.getLayer());
		Assert.assertEquals("NOR", known.getAlpha3());
	}

	private void assertCommand(ElasticsearchCommand command) {
		Assert.assertNotNull(command.getIndex());
		Assert.assertEquals("pelias", command.getIndex().getIndex());
		Assert.assertNotNull(command.getIndex().getType());
	}
}

