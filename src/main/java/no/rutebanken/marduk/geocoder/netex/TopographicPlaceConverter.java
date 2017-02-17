package no.rutebanken.marduk.geocoder.netex;

import org.rutebanken.netex.model.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.time.OffsetDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

@Component
public class TopographicPlaceConverter {
	private static final int QUEUE_SIZE = 10000;


	public void toNetexFile(TopographicPlaceReader input, String targetPath) {
		try {
			BlockingQueue<TopographicPlace> topographicPlaceQueue = new LinkedBlockingDeque(QUEUE_SIZE);

			new Thread(new ReaderTask(topographicPlaceQueue, input)).start();

			File target = new File(targetPath);
			TopographicPlaceNetexWriter netexWriter = new TopographicPlaceNetexWriter();
			netexWriter.stream(createPublicationDeliveryStructure(input), topographicPlaceQueue, new FileOutputStream(target));
		} catch (Exception e) {
			throw new RuntimeException("Conversion to Netex failed", e);
		}

	}

	private PublicationDeliveryStructure createPublicationDeliveryStructure(TopographicPlaceReader input) {
		SiteFrame siteFrame = new SiteFrame()
				                      .withCreated(OffsetDateTime.now()).withId(input.getParticipantRef() + ":SiteFrame:1")
				                      .withModification(ModificationEnumeration.NEW).withVersion("any");

		return new PublicationDeliveryStructure()
				       .withParticipantRef(input.getParticipantRef())
				       .withPublicationTimestamp(OffsetDateTime.now())
				       .withDescription(input.getDescription())
				       .withDataObjects(new PublicationDeliveryStructure.DataObjects()
						                        .withCompositeFrameOrCommonFrame(new ObjectFactory().createSiteFrame(siteFrame)));
	}


	private class ReaderTask implements Runnable {

		private BlockingQueue<TopographicPlace> queue;

		private TopographicPlaceReader input;

		public ReaderTask(BlockingQueue<TopographicPlace> queue, TopographicPlaceReader input) {
			this.queue = queue;
			this.input = input;
		}

		@Override
		public void run() {
			try {
				input.addToQueue(queue);
				queue.put(createPoisonPill());
			} catch (Exception e) {
				// TODO exception causes main thread to hang
				throw new RuntimeException("Reading topographic places failed", e);
			}

		}

		private TopographicPlace createPoisonPill() {
			TopographicPlace topographicPlace = new TopographicPlace();
			topographicPlace.setId("POISON");
			return topographicPlace;
		}

	}

}
