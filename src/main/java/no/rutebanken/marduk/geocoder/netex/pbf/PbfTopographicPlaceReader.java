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
