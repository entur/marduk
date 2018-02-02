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

package no.rutebanken.marduk.geocoder.routes.pelias.mapper.kartverket;


import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.rutebanken.marduk.geocoder.routes.pelias.json.AddressParts;
import no.rutebanken.marduk.geocoder.routes.pelias.json.Parent;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import no.rutebanken.marduk.geocoder.routes.pelias.mapper.coordinates.GeometryTransformer;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class AddressStreamToElasticsearchCommandsTest {
    private static final Long ADDRESS_POPULARITY = 7L;
    private static final Long ADDRESS_STREET_POPULARITY = 9L;

    @Test
    public void testStreamAddressesToIndexCommands() throws Exception {
        AddressStreamToElasticSearchCommands transformer = new AddressStreamToElasticSearchCommands(new AddressToPeliasMapper(ADDRESS_POPULARITY), new AddressToStreetMapper(ADDRESS_STREET_POPULARITY));

        Collection<ElasticsearchCommand> commands = transformer
                                                            .transform(new FileInputStream("src/test/resources/no/rutebanken/marduk/geocoder/csv/addresses.csv"));


        Assert.assertEquals(37, commands.size());


        commands.forEach(c -> assertCommand(c));

        List<PeliasDocument> documents = commands.stream().map(c -> (PeliasDocument) c.getSource()).collect(Collectors.toList());
        Assert.assertEquals("Should be 9 streets", 9, documents.stream().filter(d -> PeliasDocument.DEFAULT_SOURCE.equals(d.getSource())).collect(Collectors.toList()).size());

        PeliasDocument knownDocument = commands.stream().map(c -> (PeliasDocument) c.getSource()).filter(d -> d.getSourceId().endsWith("87033123")).collect(Collectors.toList()).get(0);
        assertKnownAddress(knownDocument);
    }


    // 87033123;Vegadresse;0125;1850;Bergheimveien;Bergheimveien;14;A;57;14;0;0;;;;;23;6607447.1;293546.2;508;KIRKÅS/ENGA;02030103;Mysen;121;Mysen;1;EIDSBERG;1850;MYSEN;
    private void assertKnownAddress(PeliasDocument known) throws Exception {

        AddressParts addressParts = known.getAddressParts();

        Assert.assertEquals("Bergheimveien", addressParts.getStreet());
        Assert.assertEquals("14A", addressParts.getNumber());
        Assert.assertEquals("1850", addressParts.getZip());
        Assert.assertEquals("Bergheimveien", addressParts.getName());

        Point utm33Point = new GeometryFactory().createPoint(new Coordinate(293546.2, 6607447.1));
        Point wgs84Point = GeometryTransformer.fromUTM(utm33Point, "33");

        Assert.assertEquals(wgs84Point.getY(), known.getCenterPoint().getLat(), 0.0001);
        Assert.assertEquals(wgs84Point.getX(), known.getCenterPoint().getLon(), 0.0001);

        Parent parent = known.getParent();
        Assert.assertEquals("NOR", parent.getCountryId());
        Assert.assertEquals("1850", parent.getPostalCodeId());
        Assert.assertEquals("01", parent.getCountyId());
        Assert.assertEquals("0125", parent.getLocalityId());
        Assert.assertEquals("01250508", parent.getBoroughId());
        Assert.assertEquals("Kirkås/Enga", parent.getBorough());

        Assert.assertEquals(ADDRESS_POPULARITY, known.getPopularity());

        Assert.assertEquals("Bergheimveien 14A", known.getNameMap().get("default"));
        Assert.assertEquals(Arrays.asList("Vegadresse"), known.getCategory());
    }

    private void assertCommand(ElasticsearchCommand command) {
        Assert.assertNotNull(command.getIndex());
        Assert.assertEquals("pelias", command.getIndex().getIndex());
        Assert.assertEquals("address", command.getIndex().getType());
    }
}
