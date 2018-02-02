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

package no.rutebanken.marduk.geocoder.routes.pelias.mapper;

import com.vividsolutions.jts.geom.Point;
import no.rutebanken.marduk.geocoder.geojson.KartverketLocality;
import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.rutebanken.marduk.geocoder.routes.pelias.json.GeoPoint;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import no.rutebanken.marduk.geocoder.services.AdminUnitRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class PeliasIndexParentInfoEnricherTest {

    @Mock
    private AdminUnitRepository adminUnitRepository;

    @Mock
    private KartverketLocality locality;

    private PeliasIndexParentInfoEnricher parentInfoEnricher = new PeliasIndexParentInfoEnricher();


    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testAddParentInfoByReverseGeoLookup() {
        PeliasDocument doc = new PeliasDocument("l", "sid");
        doc.setCenterPoint(new GeoPoint(1.0, 2.0));
        ElasticsearchCommand command = ElasticsearchCommand.peliasIndexCommand(doc);

        Mockito.when(adminUnitRepository.getLocality(Mockito.any(Point.class))).thenReturn(locality);
        Mockito.when(locality.getId()).thenReturn("0103");
        Mockito.when(locality.getParentId()).thenReturn("01");

        Mockito.when(adminUnitRepository.getAdminUnitName("0103")).thenReturn("GokkLocality");
        Mockito.when(adminUnitRepository.getAdminUnitName("01")).thenReturn("GokkCounty");

        parentInfoEnricher.addMissingParentInfo(command, adminUnitRepository);

        Assert.assertEquals("GokkCounty", doc.getParent().getCounty());
        Assert.assertEquals("GokkLocality", doc.getParent().getLocality());
    }
}
