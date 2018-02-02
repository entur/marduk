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

package no.rutebanken.marduk.geocoder.routes.pelias.mapper.geojson;


import no.rutebanken.marduk.geocoder.geojson.GeojsonFeatureWrapperFactory;
import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileInputStream;
import java.util.Collection;
import java.util.stream.Collectors;

public class FylkeToElasticsearchCommandTest {

	@Test
	public void testTransform() throws Exception {
		KartverketGeoJsonStreamToElasticsearchCommands transformer = new KartverketGeoJsonStreamToElasticsearchCommands(new GeojsonFeatureWrapperFactory(null),1);
		Collection<ElasticsearchCommand> commands = transformer
				                                            .transform(new FileInputStream("src/test/resources/no/rutebanken/marduk/geocoder/geojson/fylker.geojson"));

		Assert.assertEquals(4, commands.size());

		commands.forEach(c -> assertCommand(c));

		PeliasDocument kalland = commands.stream().map(c -> (PeliasDocument) c.getSource()).filter(d -> "Buskerud".equals(d.getDefaultName())).collect(Collectors.toList()).get(0);
		assertBuskerud(kalland);
	}

	private void assertBuskerud(PeliasDocument buskerud) {
		Assert.assertNotNull(buskerud.getShape());
		Assert.assertEquals("NOR", buskerud.getParent().getCountryId());
		Assert.assertEquals("06", buskerud.getSourceId());
	}

	private void assertCommand(ElasticsearchCommand command) {
		Assert.assertNotNull(command.getIndex());
		Assert.assertEquals("pelias", command.getIndex().getIndex());
		Assert.assertEquals("county", command.getIndex().getType());
	}

}
