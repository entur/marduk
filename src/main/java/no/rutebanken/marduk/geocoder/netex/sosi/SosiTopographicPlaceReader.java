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

package no.rutebanken.marduk.geocoder.netex.sosi;


import no.rutebanken.marduk.geocoder.netex.TopographicPlaceMapper;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceReader;
import no.rutebanken.marduk.geocoder.sosi.SosiElementWrapperFactory;
import no.rutebanken.marduk.geocoder.sosi.SosiTopographicPlaceAdapterReader;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.TopographicPlace;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;

public class SosiTopographicPlaceReader implements TopographicPlaceReader {
    private static final String LANGUAGE = "en";

    private static final String PARTICIPANT_REF = "KVE";
    private Collection<File> sosiFiles;

    private SosiElementWrapperFactory wrapperFactory;

    public SosiTopographicPlaceReader(SosiElementWrapperFactory wrapperFactory, Collection<File> sosiFiles) {
        this.sosiFiles = sosiFiles;
        this.wrapperFactory = wrapperFactory;
    }

    public void addToQueue(BlockingQueue<TopographicPlace> queue) throws IOException, InterruptedException {
        for (File file : sosiFiles) {
            new SosiTopographicPlaceAdapterReader(wrapperFactory, new FileInputStream(file)).read().forEach(a -> queue.add(new TopographicPlaceMapper(a, getParticipantRef()).toTopographicPlace()));
        }
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
