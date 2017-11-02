package no.rutebanken.marduk.geocoder.netex;

import org.rutebanken.netex.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

@Component
public class TopographicPlaceConverter {
    private static final int QUEUE_SIZE = 10000;
    private final Logger logger = LoggerFactory.getLogger(getClass());


    private String defaultTimeZone;

    public TopographicPlaceConverter(@Value("${tiamat.netex.import.time.zone:CET}") String defaultTimeZone) {
        this.defaultTimeZone = defaultTimeZone;
    }

    public void toNetexFile(TopographicPlaceReader input, String targetPath) {
        try {
            BlockingQueue<TopographicPlace> topographicPlaceQueue = new LinkedBlockingDeque<>(QUEUE_SIZE);

            ReaderTask reader = new ReaderTask(topographicPlaceQueue, input);
            new Thread(reader).start();

            File target = new File(targetPath);
            TopographicPlaceNetexWriter netexWriter = new TopographicPlaceNetexWriter();
            netexWriter.stream(createPublicationDeliveryStructure(input), topographicPlaceQueue, new FileOutputStream(target));

            reader.verify();
        } catch (Exception e) {
            throw new RuntimeException("Conversion to Netex failed with exception: " + e.getMessage(), e);
        }

    }

    private PublicationDeliveryStructure createPublicationDeliveryStructure(TopographicPlaceReader input) {
        VersionFrameDefaultsStructure frameDefaultsStructure = new VersionFrameDefaultsStructure().withDefaultLocale(new LocaleStructure().withTimeZone(defaultTimeZone));
        SiteFrame siteFrame = new SiteFrame()
                                      .withCreated(LocalDateTime.now()).withId(input.getParticipantRef() + ":SiteFrame:1")
                                      .withModification(ModificationEnumeration.NEW).withVersion("any").withFrameDefaults(frameDefaultsStructure);

        return new PublicationDeliveryStructure()
                       .withParticipantRef(input.getParticipantRef())
                       .withPublicationTimestamp(LocalDateTime.now())
                       .withDescription(input.getDescription())
                       .withDataObjects(new PublicationDeliveryStructure.DataObjects()
                                                .withCompositeFrameOrCommonFrame(new ObjectFactory().createSiteFrame(siteFrame)));
    }


    private class ReaderTask implements Runnable {

        private BlockingQueue<TopographicPlace> queue;

        private TopographicPlaceReader input;

        private Exception exception;

        public ReaderTask(BlockingQueue<TopographicPlace> queue, TopographicPlaceReader input) {
            this.queue = queue;
            this.input = input;
        }

        @Override
        public void run() {
            try {
                input.addToQueue(queue);

            } catch (Exception e) {
                exception = e;
            } finally {
                try {
                    queue.put(createPoisonPill());
                } catch (InterruptedException ie) {
                    logger.info("Reading topographic places interrupted", ie);
                }
            }

        }

        public void verify() throws Exception {
            if (exception != null) {
                throw exception;
            }
        }

        private TopographicPlace createPoisonPill() {
            TopographicPlace topographicPlace = new TopographicPlace();
            topographicPlace.setId("POISON");
            return topographicPlace;
        }

    }

}
