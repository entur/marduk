package no.rutebanken.marduk.routes.blobstore;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.Utils;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class TimetableGetExportBlobRouteBuilder extends BaseRouteBuilder {

    @Value("#{'${timetable.export.blob.prefixes:outbound/gtfs/,outbound/netex/}'.split(',')}")
    private List<String> staticPrefixes;

    @Value("${timetable.export.graph.months:2}")
    private int noOfMonthsToFetchGraphBlobsFor;


    @Value("${otp.graph.blobstore.subdirectory}")
    private String blobStoreSubdirectory;

    @Override
    public void configure() throws Exception {
        super.configure();


        from("direct:listTimetableExportAndGraphBlobs")
                .process(e -> e.getIn().setHeader(Constants.FILE_PARENT_COLLECTION, calculatePrefixes()))
                .to("direct:listBlobsInFolders")
                .routeId("timetable-get-export-blobs");
    }

    private Set<String> calculatePrefixes() {
        Set<String> prefixes = new HashSet<>();
        prefixes.addAll(staticPrefixes);

        DateTimeFormatter graphPrefixFormatter = DateTimeFormatter.ofPattern("yyyyMM");
        int monthsAgo = 0;
        LocalDate today = LocalDate.now();

        while (monthsAgo < noOfMonthsToFetchGraphBlobsFor) {
            String graphPrefixForMonth = today.minusMonths(monthsAgo).format(graphPrefixFormatter);
            prefixes.add(blobStoreSubdirectory + "/" + Utils.getOtpVersion() + "/" + graphPrefixForMonth);
            monthsAgo++;
        }

        return prefixes;
    }
}
