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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * Utility class for file management.
 */
public class MardukFileUtils {

    private MardukFileUtils() {
    }


    public static File createTempFile(byte[] data, String prefix, String suffix) throws IOException {
        File inputFile = File.createTempFile(prefix, suffix);
        try (FileOutputStream fos = new FileOutputStream(inputFile)) {
            fos.write(data);
        }
        return inputFile;
    }


    /**
     * Open an input stream on a temporary file with the guarantee that the file will be delete when the stream is closed.
     *
     * @param tmpFile
     * @return
     * @throws IOException
     */
    public static InputStream createDeleteOnCloseInputStream(File tmpFile) throws IOException {
        return Files.newInputStream(tmpFile.toPath(), StandardOpenOption.DELETE_ON_CLOSE);
    }


    /**
     * Return true if the fileName does not contain new lines, tabs and any non-ISO_8859_1 characters.
     *
     * @param fileName the file name to test.
     * @return true if the fileName does not contain  new lines, tab and any non-ISO_8859_1 characters
     */
    public static boolean isValidFileName(String fileName) {
        return !fileName.contains("\n")
                && !fileName.contains("\r")
                && !fileName.contains("\t")
                && StandardCharsets.ISO_8859_1.newEncoder().canEncode(fileName);
    }

    /**
     * Remove new lines, tabs and any non-ISO_8859_1 characters from file name.
     * New lines and tabs may cause security issues.
     * Non-ISO_8859_1 characters cause chouette import to crash.
     *
     * @param fileName the file name to sanitize.
     * @return a file name where new lines, tab and any non ISO_8859_1 characters are filtered out.
     */
    public static String sanitizeFileName(String fileName) {

        StringBuilder result = new StringBuilder(fileName.length());
        for (char val : fileName.toCharArray()) {

            if (val == '\n' || val == '\r' || val == '\t') {
                continue;
            }

            if (StandardCharsets.ISO_8859_1.newEncoder().canEncode(val)) {
                result.append(val);
            }
        }
        return result.toString();
    }

}