package no.rutebanken.marduk.geocoder.netex;


import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.TopographicPlace;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;

public interface TopographicPlaceReader {

	String getParticipantRef();

	MultilingualString getDescription();

	void addToQueue(BlockingQueue<TopographicPlace> queue) throws IOException, InterruptedException;
}
