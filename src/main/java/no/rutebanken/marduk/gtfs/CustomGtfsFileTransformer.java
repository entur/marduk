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
import org.onebusaway.gtfs_transformer.GtfsTransformer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;


public abstract class CustomGtfsFileTransformer {

    public File transform(File inputFile) {
        try {
            GtfsTransformer transformer = new GtfsTransformer();
            File outputFile = Path.of(inputFile.getParent()).resolve("transformed-" + inputFile.getName()).toFile();
            transformer.setGtfsInputDirectories(Collections.singletonList(inputFile));
            transformer.setOutputDirectory(outputFile);

            addCustomTransformations(transformer);

            transformer.getReader().setOverwriteDuplicates(true);
            transformer.run();

            Files.delete(inputFile.toPath());

            return outputFile;
        } catch (Exception e) {
            throw new MardukException("Gtfs transformation failed with exception: " + e.getMessage(), e);
        }
    }

    protected abstract void addCustomTransformations(GtfsTransformer transformer);
}
