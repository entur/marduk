package no.rutebanken.marduk.geocoder.netex.sosi;


import no.rutebanken.marduk.geocoder.netex.TopographicPlaceMapper;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceReader;
import no.rutebanken.marduk.geocoder.sosi.SosiTopographicPlaceAdapterReader;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.TopographicPlace;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;

public class SosiTopographicPlaceReader implements TopographicPlaceReader {
    private static final String LANGUAGE = "en";

    private static final String PARTICIPANT_REF = "KVE";
    private File sosiFile;

    private InputStream sosiInputStream;

    public SosiTopographicPlaceReader(File sosiFile) {
        this.sosiFile = sosiFile;
    }

    public SosiTopographicPlaceReader(InputStream sosiInputStream) {
        this.sosiInputStream = sosiInputStream;
    }


    public void addToQueue(BlockingQueue<TopographicPlace> queue) throws IOException, InterruptedException {
        if (sosiInputStream == null) {
            sosiInputStream = new FileInputStream(sosiFile);
        }
        new SosiTopographicPlaceAdapterReader(sosiInputStream).read().forEach(a -> queue.add(new TopographicPlaceMapper(a, getParticipantRef()).toTopographicPlace()));
    }


    @Override
    public String getParticipantRef() {
        return PARTICIPANT_REF;
    }

    @Override
    public MultilingualString getDescription() {
        return new MultilingualString().withLang(LANGUAGE).withValue("Kartverket administrative units");
    }
}
