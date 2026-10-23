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

package no.rutebanken.marduk.exceptions;

/**
 * Failure of an operation on the emergency controls of the OTP graph publication, with the HTTP status returned by
 * the admin API.
 */
public class GraphControlException extends MardukException {

    private static final long serialVersionUID = 1L;

    private final int status;

    public GraphControlException(int status, String message) {
        super(message);
        this.status = status;
    }

    public GraphControlException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public static GraphControlException badRequest(String message) {
        return new GraphControlException(400, message);
    }

    public static GraphControlException notFound(String message) {
        return new GraphControlException(404, message);
    }

    public int getStatus() {
        return status;
    }
}
