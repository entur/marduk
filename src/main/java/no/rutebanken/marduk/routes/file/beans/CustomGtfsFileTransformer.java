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

package no.rutebanken.marduk.routes.file.beans;

import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.onebusaway.gtfs_transformer.GtfsTransformer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;

import static no.rutebanken.marduk.routes.file.GtfsFileUtils.FEED_INFO_FILE_NAME;

public abstract class CustomGtfsFileTransformer {

    public File transform(File inputFile, ByteArrayOutputStream feedInfo) {
        try {
            GtfsTransformer transformer = new GtfsTransformer();
            File outputFile = File.createTempFile("marduk-cleanup", ".zip");

            transformer.setGtfsInputDirectories(Arrays.asList(inputFile));
            transformer.setOutputDirectory(outputFile);

            addCustomTransformations(transformer);

            transformer.getReader().setOverwriteDuplicates(true);
            transformer.run();

            ZipFileUtils.replaceFileInZipFile(outputFile, FEED_INFO_FILE_NAME, feedInfo);
            return outputFile;
        } catch (Exception e) {
            throw new RuntimeException("Gtfs transformation failed with exception: " + e.getMessage(), e);
        }
    }

    protected abstract void addCustomTransformations(GtfsTransformer transformer);
}
