package no.rutebanken.marduk.geocoder.routes.pelias.mapper.geojson;

import no.rutebanken.marduk.geocoder.geojson.GeojsonFeatureWrapperFactory;
import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class PlaceNamesStreamToElasticsearchCommandTest {


    @Test
    public void testTransform() throws Exception {
        List<String> whiteList = Arrays.asList("106", "107");
        KartverketGeoJsonStreamToElasticsearchCommands transformer = new KartverketGeoJsonStreamToElasticsearchCommands(new GeojsonFeatureWrapperFactory(whiteList));
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

        Assert.assertEquals("NOR", kalland.getParent().getCountryId());
        Assert.assertEquals("10", kalland.getParent().getCountyId());
        Assert.assertEquals("1002", kalland.getParent().getLocalityId());
        Assert.assertEquals(Arrays.asList("107"), kalland.getCategory());
    }

    private void assertCommand(ElasticsearchCommand command) {
        Assert.assertNotNull(command.getIndex());
        Assert.assertEquals("pelias", command.getIndex().getIndex());
        Assert.assertEquals("address", command.getIndex().getType());
    }

}
