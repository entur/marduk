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

    private static final Long POI_POPULARITY = 5l;

    @Test
    public void testTransform() throws Exception {
        DeliveryPublicationStreamToElasticsearchCommands mapper =
                new DeliveryPublicationStreamToElasticsearchCommands(new StopPlaceBoostConfiguration("{\"defaultValue\":1000, \"stopTypeFactors\":{\"airport\":{\"*\":3},\"onstreetBus\":{\"*\":2}}}"), POI_POPULARITY, Arrays.asList("leisure=stadium", "building=church"));

        Collection<ElasticsearchCommand> commands = mapper
                                                            .transform(new FileInputStream("src/test/resources/no/rutebanken/marduk/geocoder/netex/tiamat-export.xml"));

        Assert.assertEquals(10, commands.size());
        commands.forEach(c -> assertCommand(c));

        assertKnownPoi(byId(commands, "NSR:TopographicPlace:724"));
        assertKnownStopPlace(byId(commands, "NSR:StopPlace:39231"), "Harstad/Narvik Lufthavn");
        assertKnownStopPlace(byId(commands, "NSR:StopPlace:39231-1"), "AliasName");
        assertKnownMultimodalStopPlaceParent(byId(commands, "NSR:StopPlace:1000"));
        assertKnownMultimodalStopPlaceChild(byId(commands, "NSR:StopPlace:1000a"));

        // Rail replacement bus stop should not be mapped
        assertNotMapped(commands, "NSR:StopPlace:1001");
        // Stop without quay should not be mapped
        assertNotMapped(commands, "NSR:StopPlace:1002");
        // Outdated stop should not be mapped
        assertNotMapped(commands, "NSR:StopPlace:1003");

        // POI not matching filter should not be mapped
        assertNotMapped(commands, "NSR:TopographicPlace:725");
    }

    private PeliasDocument byId(Collection<ElasticsearchCommand> commands, String sourceId) {
        return commands.stream().map(c -> (PeliasDocument) c.getSource()).filter(d -> d.getSourceId().equals(sourceId)).collect(Collectors.toList()).get(0);
    }

    private void assertNotMapped(Collection<ElasticsearchCommand> commands, String sourceId) {
        Assert.assertTrue("Id should not have been matched", commands.stream().map(c -> (PeliasDocument) c.getSource()).noneMatch(d -> d.getSourceId().equals(sourceId)));
    }

    private void assertKnownMultimodalStopPlaceParent(PeliasDocument known) throws Exception {
        Assert.assertEquals("QuayLessParentStop", known.getDefaultName());
        Assert.assertEquals(STOP_PLACE_LAYER, known.getLayer());
        Assert.assertEquals(StopPlaceToPeliasMapper.SOURCE_PARENT_STOP_PLACE, known.getSource());
        Assert.assertEquals(known.getCategory().size(), 2);
        Assert.assertTrue(known.getCategory().containsAll(Arrays.asList("airport", "onstreetBus")));
        Assert.assertEquals("Expected popularity to be default (1000) boosted by sum of stop type boosts (airport=3, onstreetBus=2)", 5000, known.getPopularity().longValue());
    }

    private void assertKnownMultimodalStopPlaceChild(PeliasDocument known) throws Exception {
        Assert.assertEquals("Child stop - airport", known.getDefaultName());
        Assert.assertEquals(STOP_PLACE_LAYER, known.getLayer());
        Assert.assertEquals(StopPlaceToPeliasMapper.SOURCE_CHILD_STOP_PLACE, known.getSource());
        Assert.assertEquals(known.getCategory().size(), 1);
        Assert.assertTrue(known.getCategory().containsAll(Arrays.asList("airport")));
        Assert.assertEquals("Expected popularity to be default (1000) boosted by stop type boosts (airport=3)", 3000, known.getPopularity().longValue());
    }

    private void assertKnownStopPlace(PeliasDocument known, String defaultName) throws Exception {
        Assert.assertEquals(defaultName, known.getDefaultName());
        Assert.assertEquals("Harstad/Narvik Lufthavn", known.getNameMap().get("no"));
        Assert.assertEquals("Harstad/Narvik Lufthavn", known.getNameMap().get("display"));
        Assert.assertEquals(STOP_PLACE_LAYER, known.getLayer());
        Assert.assertEquals(PeliasDocument.DEFAULT_SOURCE, known.getSource());
        Assert.assertEquals(Arrays.asList("airport"), known.getCategory());
        Assert.assertEquals(68.490412, known.getCenterPoint().getLat(), 0.0001);
        Assert.assertEquals(16.687364, known.getCenterPoint().getLon(), 0.0001);
        Assert.assertEquals(Arrays.asList("AKT:TariffZone:505"), known.getTariffZones());
        Assert.assertEquals("Expected popularity to be default (1000) boosted by stop type (airport)", 3000, known.getPopularity().longValue());
    }


    private void assertKnownPoi(PeliasDocument known) throws Exception {
        Assert.assertEquals("Stranda kyrkje", known.getDefaultName());
        Assert.assertEquals("Stranda kyrkje", known.getNameMap().get("no"));
        Assert.assertEquals("address", known.getLayer());
        Assert.assertEquals(PeliasDocument.DEFAULT_SOURCE, known.getSource());
        Assert.assertEquals(Arrays.asList("poi"), known.getCategory());
        Assert.assertEquals(62.308413, known.getCenterPoint().getLat(), 0.0001);
        Assert.assertEquals(6.947573, known.getCenterPoint().getLon(), 0.0001);
        Assert.assertEquals(POI_POPULARITY, known.getPopularity());
    }


    private void assertCommand(ElasticsearchCommand command) {
        Assert.assertNotNull(command.getIndex());
        Assert.assertEquals("pelias", command.getIndex().getIndex());
        Assert.assertNotNull(command.getIndex().getType());
    }
}

