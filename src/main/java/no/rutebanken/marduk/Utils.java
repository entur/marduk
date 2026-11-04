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

package no.rutebanken.marduk;

import java.util.List;

public class Utils {

    private  Utils() {
    }

    public static Long getLastPathElementOfUrl(String url) {
        if (url == null) {
            throw new IllegalArgumentException("Url is null");
        }
        return Long.valueOf(url.substring(url.lastIndexOf('/') + 1));
    }

    /**
     * The value with every carriage return and line feed removed.
     *
     * <p>Applied where a caller-supplied string enters rather than where it is logged, because a value that
     * could forge a line in the log goes on to become a blob path, a message header and a Chouette query
     * parameter, and has no business in any of those either. Nothing this is used on - a codespace, a file
     * name, a Chouette job id, a job status - can legitimately span two lines.
     */
    public static String singleLine(String value) {
        return value == null ? null : value.replace("\r", "").replace("\n", "");
    }

    /** Every element of the list, as {@link #singleLine(String)} leaves it. */
    public static List<String> singleLine(List<String> values) {
        return values == null ? null : values.stream().map(Utils::singleLine).toList();
    }
}
