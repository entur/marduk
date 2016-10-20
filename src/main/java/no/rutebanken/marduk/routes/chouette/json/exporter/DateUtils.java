package no.rutebanken.marduk.routes.chouette.json.exporter;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

public class DateUtils {

    public static Date startDateFor(long daysBack){
        return Date.from(LocalDate.now().atStartOfDay().minusDays(daysBack).atZone(ZoneId.systemDefault()).toInstant());
    }

}
