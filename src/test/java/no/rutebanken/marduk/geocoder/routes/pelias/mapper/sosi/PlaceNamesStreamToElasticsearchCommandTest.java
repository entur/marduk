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

package no.rutebanken.marduk.geocoder.routes.pelias.mapper.sosi;

import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import no.rutebanken.marduk.geocoder.routes.pelias.mapper.kartverket.KartverketSosiStreamToElasticsearchCommands;
import no.rutebanken.marduk.geocoder.sosi.SosiElementWrapperFactory;
import no.rutebanken.marduk.geocoder.sosi.SosiFileFilter;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public class PlaceNamesStreamToElasticsearchCommandTest {


    private static final Long placePopularity = 3l;

    private SosiElementWrapperFactory sosiElementWrapperFactory = new SosiElementWrapperFactory();


    @Test
    public void testTransform() throws Exception {
        Collection<ElasticsearchCommand> commands = new KartverketSosiStreamToElasticsearchCommands(sosiElementWrapperFactory, placePopularity).transform(new FileInputStream("src/test/resources/no/rutebanken/marduk/geocoder/sosi/placeNames.sos"));

        Assert.assertEquals(2, commands.size());

        commands.forEach(c -> assertCommand(c));

        PeliasDocument kalland = commands.stream().map(c -> (PeliasDocument) c.getSource()).filter(d -> "Stornesodden".equals(d.getDefaultName())).collect(Collectors.toList()).get(0);
        assertKalland(kalland);
    }

    private void assertKalland(PeliasDocument kalland) {
        Assert.assertEquals(58.71085, kalland.getCenterPoint().getLat().doubleValue(), 0.0001);
        Assert.assertEquals(7.397255, kalland.getCenterPoint().getLon().doubleValue(), 0.0001);

        Assert.assertEquals("NOR", kalland.getParent().getCountryId());
        Assert.assertEquals(Arrays.asList("industriområde"), kalland.getCategory());
        Assert.assertEquals(placePopularity, kalland.getPopularity());
    }

    private void assertCommand(ElasticsearchCommand command) {
        Assert.assertNotNull(command.getIndex());
        Assert.assertEquals("pelias", command.getIndex().getIndex());
        Assert.assertEquals("address", command.getIndex().getType());
    }

}
