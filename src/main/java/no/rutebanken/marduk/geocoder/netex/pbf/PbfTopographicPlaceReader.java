package no.rutebanken.marduk.geocoder.netex.pbf;

import crosby.binary.file.BlockInputStream;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceReader;
import org.opentripplanner.openstreetmap.impl.BinaryOpenStreetMapParser;
import org.opentripplanner.openstreetmap.services.OpenStreetMapContentHandler;
import org.rutebanken.netex.model.IanaCountryTldEnumeration;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.TopographicPlace;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Map osm pbf places of interest to Netex topographic place.
 */
public class PbfTopographicPlaceReader implements TopographicPlaceReader {

	private File[] files;

	private List<String> filter;

	private IanaCountryTldEnumeration countryRef;

	private static final String LANGUAGE = "en";

	private static final String PARTICIPANT_REF = "OSM";

	public PbfTopographicPlaceReader(List<String> filter, IanaCountryTldEnumeration countryRef, File... files) {
		this.files = files;
		this.filter = filter;
		this.countryRef = countryRef;
	}

	@Override
	public String getParticipantRef() {
		return PARTICIPANT_REF;
	}

	@Override
	public MultilingualString getDescription() {
		return new MultilingualString().withLang(LANGUAGE).withValue("Kartverket administrative units");
	}

	@Override
	public void addToQueue(BlockingQueue<TopographicPlace> queue) throws IOException, InterruptedException {
		for (File file : files) {
			OpenStreetMapContentHandler contentHandler = new TopographicPlaceOsmContentHandler(queue, filter, PARTICIPANT_REF, countryRef);
			BinaryOpenStreetMapParser parser = new BinaryOpenStreetMapParser(contentHandler);
			parser.setParseRelations(false);

			// Parse ways to collect nodes first
			parser.setParseNodes(false);
			new BlockInputStream(new FileInputStream(file), parser).process();
			contentHandler.doneSecondPhaseWays();

			// Parse nodes and ways
			parser.setParseNodes(true);
			new BlockInputStream(new FileInputStream(file), parser).process();
		}
	}
}
