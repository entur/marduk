package no.rutebanken.marduk.geocoder.routes.pelias.mapper.sosi;

import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import no.rutebanken.marduk.geocoder.routes.pelias.mapper.kartverket.KartverketSosiStreamToElasticsearchCommands;
import no.rutebanken.marduk.geocoder.sosi.SosiElementWrapperFactory;
import no.rutebanken.marduk.geocoder.sosi.SosiFileFilter;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileInputStream;
import java.util.Collection;

public class SosiFileFilterTest {

    @Test
    public void filterByType() throws Exception {
        String targetFile = "target/filtered.sos";
        new SosiFileFilter().filterElements(new FileInputStream("src/test/resources/no/rutebanken/marduk/geocoder/sosi/placeNames.sos"), targetFile, kv -> "NAVNEOBJEKTTYPE".equals(kv.getKey()) && "industriområde".equals(kv.getValue()));

        Collection<ElasticsearchCommand> commands = new KartverketSosiStreamToElasticsearchCommands(new SosiElementWrapperFactory(), 1).transform(new FileInputStream(targetFile));

        Assert.assertEquals(1, commands.size());

        Assert.assertEquals(((PeliasDocument) commands.iterator().next().getSource()).getDefaultName(), "Stornesodden");
    }

}
