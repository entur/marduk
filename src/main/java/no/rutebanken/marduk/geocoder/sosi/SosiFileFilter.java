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

package no.rutebanken.marduk.geocoder.sosi;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Function;

@Service
public class SosiFileFilter {

    private static final String ENCODING = "utf-8";

    /**
     * Create a copy of a SOSI file, containing only elements that match provided matcher function.
     */
    public void filterElements(InputStream orgFile, String filteredFile, Function<Pair<String, String>, Boolean> matcher) {

        boolean currentMatch = false;
        StringBuilder currentElement = new StringBuilder();

        boolean headerRead = false;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(orgFile, ENCODING))) {

            FileOutputStream fos = new FileOutputStream(filteredFile);
            String line;
            while ((line = br.readLine()) != null) {

                boolean startOfElement = isStartOfElement(line);
                if (!headerRead) {
                    if (startOfElement) {
                        headerRead = true;
                        currentElement.append(line).append("\n");
                    } else {
                        IOUtils.write(line + "\n", fos, ENCODING);
                    }
                } else {
                    if (startOfElement) {
                        if (currentMatch) {
                            IOUtils.write(currentElement.toString(), fos, ENCODING);
                        }
                        currentElement = new StringBuilder();
                        currentMatch = false;
                    }

                    currentElement.append(line).append("\n");
                    if (matcher.apply(splitLine(line))) {
                        currentMatch = true;
                    }

                }
            }

            IOUtils.write(currentElement.toString(), fos, ENCODING);
        } catch (IOException ioe) {
            throw new RuntimeException("Failed to split SOSI file:" + ioe.getMessage(), ioe);
        }


    }

    private Pair<String, String> splitLine(String line) {
        String field = line.replaceFirst("^\\.+(?!$)", "");
        int split = field.indexOf(" ");

        if (split >= 0) {

            String key = field.substring(0, split);
            String value = field.substring(split + 1, field.length());
            return Pair.of(key, value);
        }
        return Pair.of(field, null);
    }

    private boolean isStartOfElement(String line) {
        return line.startsWith(".") && !line.startsWith("..") && !line.startsWith(".HODE");
    }

}