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

package no.rutebanken.marduk.routes.file;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class TempFileUtils {


    public static File createTempFile(byte[] data, String prefix, String suffix) throws IOException {
        File inputFile = File.createTempFile(prefix, suffix);
        try(FileOutputStream fos = new FileOutputStream(inputFile)) {
            fos.write(data);
        }
        return inputFile;
    }


    public static InputStream createDeleteOnCloseInputStream(File tmpFile) throws IOException {
        return Files.newInputStream(tmpFile.toPath(), StandardOpenOption.DELETE_ON_CLOSE);
    }





}