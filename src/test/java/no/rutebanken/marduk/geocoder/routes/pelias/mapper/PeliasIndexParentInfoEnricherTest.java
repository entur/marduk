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
		PeliasDocument doc = new PeliasDocument("l", "s", "sid");
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
