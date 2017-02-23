package no.rutebanken.marduk.geocoder.routes.pelias.mapper.kartverket;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.rutebanken.marduk.geocoder.routes.pelias.json.AddressParts;
import no.rutebanken.marduk.geocoder.routes.pelias.json.GeoPoint;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import no.rutebanken.marduk.geocoder.routes.pelias.kartverket.KartverketAddress;
import no.rutebanken.marduk.geocoder.routes.pelias.kartverket.KartverketAddressReader;
import no.rutebanken.marduk.geocoder.routes.pelias.mapper.GeometryTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AddressStreamToElasticSearchCommands {

	private static final String TYPE = "address";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private GeometryFactory factory = new GeometryFactory();

	public Collection<ElasticsearchCommand> transform(InputStream addressStream) {
		return new KartverketAddressReader().read(addressStream).stream().map(a -> toCommand(a)).collect(Collectors.toList());
	}


	private ElasticsearchCommand toCommand(KartverketAddress address) {
		return ElasticsearchCommand.indexCommand("pelias", TYPE, null, toPeliasDocument(address));
	}

	private PeliasDocument toPeliasDocument(KartverketAddress address) {
		PeliasDocument document = new PeliasDocument();
		document.setLayer(PeliasDocument.LAYER_NEIGHBOURHOOD);
		document.setSource("Kartverket");
		document.setSourceId("AddressId:" + address.getAddresseId());

		document.setAddressParts(toAddressParts(address));
		document.setCenterPoint(toCenterPoint(address));
		return document;
	}

	private GeoPoint toCenterPoint(KartverketAddress address) {
		if (address.getNord() == null || address.getOst() == null) {
			return null;
		}

		String utmZone = KartverketCoordinatSystemMapper.toUTMZone(address.getKoordinatsystemKode());
		if (utmZone == null) {
			logger.info("Ignoring center point for address with non-utm coordinate system: " + address.getKoordinatsystemKode());
			return null;
		}

		Point p = factory.createPoint(new Coordinate(address.getOst(), address.getNord()));

		try {
			Point conv = GeometryTransformer.fromUTM(p, utmZone);

			return new GeoPoint(conv.getX(), conv.getY());
		} catch (Exception e) {
			logger.info("Ignoring center point for address (" + address.getAddresseId() + ") where geometry transformation failed: " + address.getKoordinatsystemKode());
		}


		return null;
	}


	private AddressParts toAddressParts(KartverketAddress address) {
		AddressParts addressParts = new AddressParts();

		addressParts.setStreet(address.getAddressenavn());
		addressParts.setNumber(address.getNr());
		addressParts.setZip(address.getPostnrn());
		return addressParts;
	}

}
