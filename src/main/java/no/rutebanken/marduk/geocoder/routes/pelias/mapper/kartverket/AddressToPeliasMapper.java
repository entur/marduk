package no.rutebanken.marduk.geocoder.routes.pelias.mapper.kartverket;


import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import no.rutebanken.marduk.geocoder.routes.pelias.json.*;
import no.rutebanken.marduk.geocoder.routes.pelias.kartverket.KartverketAddress;
import no.rutebanken.marduk.geocoder.routes.pelias.mapper.GeometryTransformer;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class AddressToPeliasMapper {

	private static final String SOURCE = "kartverket";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private GeometryFactory factory = new GeometryFactory();

	public PeliasDocument toPeliasDocument(KartverketAddress address) {
		PeliasDocument document = new PeliasDocument("address", SOURCE, address.getAddresseId());
		document.setAddressParts(toAddressParts(address));
		document.setCenterPoint(toCenterPoint(address));
		document.setParent(toParent(address));

		document.setDefaultName(toName(address));
		document.setAlpha3("NOR");
		document.setCategory(Arrays.asList(address.getType()));
		return document;
	}

	private String toName(KartverketAddress address) {
		return address.getAddressenavn() + " " + address.getNr() + address.getBokstav();
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
			return new GeoPoint(conv.getY(), conv.getX());
		} catch (Exception e) {
			logger.info("Ignoring center point for address (" + address.getAddresseId() + ") where geometry transformation failed: " + address.getKoordinatsystemKode());
		}

		return null;
	}

	private Parent toParent(KartverketAddress address) {
		return Parent.builder().withPostalCodeId(address.getPostnrn())
				       .withCountryId("NOR")
				       .withCountyId( address.getFylkesNo())
					   .withlocality(address.getFullKommuneNo())
				       .withlocalityId( address.getFullKommuneNo())
				       .withBoroughId(address.getFullGrunnkretsNo())
				       .withBorough(formatName(address.getGrunnkretsnavn()))
				       .build();
	}


	private String formatName(String name) {
		return WordUtils.capitalize(StringUtils.lowerCase((name)), ' ', '/');
	}

	private AddressParts toAddressParts(KartverketAddress address) {
		AddressParts addressParts = new AddressParts();
		addressParts.setName(address.getAddressenavn());
		addressParts.setStreet(address.getAddressenavn());
		addressParts.setNumber(address.getNr() + address.getBokstav());
		addressParts.setZip(address.getPostnrn());
		return addressParts;
	}

}
