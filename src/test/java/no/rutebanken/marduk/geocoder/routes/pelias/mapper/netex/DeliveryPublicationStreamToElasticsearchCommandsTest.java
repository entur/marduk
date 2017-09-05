package no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex;


import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex.boost.StopPlaceBoostConfiguration;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex.StopPlaceToPeliasMapper.STOP_PLACE_LAYER;

public class DeliveryPublicationStreamToElasticsearchCommandsTest {


    @Test
    public void testTransform() throws Exception {
        DeliveryPublicationStreamToElasticsearchCommands mapper =
                new DeliveryPublicationStreamToElasticsearchCommands(new StopPlaceBoostConfiguration("{\"defaultValue\":1000, \"stopTypeFactors\":{\"airport\":{\"*\":3}}}"));

        Collection<ElasticsearchCommand> commands = mapper
                                                            .transform(new FileInputStream("src/test/resources/no/rutebanken/marduk/geocoder/netex/tiamat-export.xml"));

        Assert.assertEquals(3, commands.size());
        commands.forEach(c -> assertCommand(c));


        assertKnownStopPlace(byId(commands, "NSR:StopPlace:39231"));
        assertKnownPoi(byId(commands, "NSR:TopographicPlace:724"));
        // Parent stop should be mapped
        Assert.assertNotNull(byId(commands, "NSR:StopPlace:1000"));

        // Rail replacement bus stop should not be mapped
        assertNotMapped(commands, "NSR:StopPlace:1001");
        // Stop without quay should not be mapped
        assertNotMapped(commands, "NSR:StopPlace:1002");
        // Outdated stop should not be mapped
        assertNotMapped(commands, "NSR:StopPlace:1003");
    }

    private PeliasDocument byId(Collection<ElasticsearchCommand> commands, String sourceId) {
        return commands.stream().map(c -> (PeliasDocument) c.getSource()).filter(d -> d.getSourceId().equals(sourceId)).collect(Collectors.toList()).get(0);
    }

    private void assertNotMapped(Collection<ElasticsearchCommand> commands, String sourceId) {
        Assert.assertTrue("Id should not have been matched", commands.stream().map(c -> (PeliasDocument) c.getSource()).noneMatch(d -> d.getSourceId().equals(sourceId)));
    }


    private void assertKnownStopPlace(PeliasDocument known) throws Exception {
        Assert.assertEquals("Harstad/Narvik Lufthavn", known.getDefaultName());
        Assert.assertEquals("Harstad/Narvik Lufthavn", known.getNameMap().get("no"));
        Assert.assertEquals(STOP_PLACE_LAYER, known.getLayer());
        Assert.assertEquals(Arrays.asList("airport"), known.getCategory());
        Assert.assertEquals(68.490412, known.getCenterPoint().getLat(), 0.0001);
        Assert.assertEquals(16.687364, known.getCenterPoint().getLon(), 0.0001);
        Assert.assertEquals("Expected popularity to be default (1000) boosted by stop type (airport)", 3000, known.getPopularity().longValue());
    }


    private void assertKnownPoi(PeliasDocument known) throws Exception {
        Assert.assertEquals("Stranda kyrkje", known.getDefaultName());
        Assert.assertEquals("Stranda kyrkje", known.getNameMap().get("no"));
        Assert.assertEquals("building", known.getLayer());
        Assert.assertEquals(Arrays.asList("poi"), known.getCategory());
        Assert.assertEquals(62.308413, known.getCenterPoint().getLat(), 0.0001);
        Assert.assertEquals(6.947573, known.getCenterPoint().getLon(), 0.0001);
    }


    private void assertCommand(ElasticsearchCommand command) {
        Assert.assertNotNull(command.getIndex());
        Assert.assertEquals("pelias", command.getIndex().getIndex());
        Assert.assertNotNull(command.getIndex().getType());
    }
}

