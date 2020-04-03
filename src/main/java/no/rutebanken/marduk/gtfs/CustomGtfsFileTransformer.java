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

package no.rutebanken.marduk.gtfs;

import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.routes.file.TempFileUtils;
import org.onebusaway.gtfs_transformer.GtfsTransformer;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;


public abstract class CustomGtfsFileTransformer {

    public InputStream transform(File inputFile) {
        try {
            GtfsTransformer transformer = new GtfsTransformer();
            File outputFile = File.createTempFile("marduk-gtfs-custom-transform-", ".zip");

            transformer.setGtfsInputDirectories(Collections.singletonList(inputFile));
            transformer.setOutputDirectory(outputFile);

            addCustomTransformations(transformer);

            transformer.getReader().setOverwriteDuplicates(true);
            transformer.run();

            GtfsFileUtils.addOrReplaceFeedInfo(outputFile);


            return TempFileUtils.createDeleteOnCloseInputStream(outputFile);
        } catch (Exception e) {
            throw new MardukException("Gtfs transformation failed with exception: " + e.getMessage(), e);
        }
    }

    protected abstract void addCustomTransformations(GtfsTransformer transformer);
}
