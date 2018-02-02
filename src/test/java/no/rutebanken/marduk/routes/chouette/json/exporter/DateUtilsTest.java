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

package no.rutebanken.marduk.routes.chouette.json.exporter;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static junit.framework.TestCase.assertEquals;

public class DateUtilsTest {

    @Test
    public void testStartDateFor() {
        Date startDateFor2daysBack = DateUtils.startDateFor(2L);
        LocalDate result = startDateFor2daysBack.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        assertEquals(2, ChronoUnit.DAYS.between(result, LocalDate.now()));
    }

}