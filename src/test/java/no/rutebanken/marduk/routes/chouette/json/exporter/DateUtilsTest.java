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