package no.rutebanken.marduk.routes.google;

import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class GoogleGtfsTransformationServiceTest {

    private static final String GTFS_FILE_EXTENDED_ROUTE_TYPES = "src/test/resources/no/rutebanken/marduk/routes/google/gtfs_for_google_transform.zip";

    @Test
    public void transformToGoogleFormat() throws Exception {
        File out = new GoogleGtfsTransformationService().transformToGoogleFormat(new File(GTFS_FILE_EXTENDED_ROUTE_TYPES));

        assertRouteRouteTypesAreConverted(out);


        assertStopVehicleTypesAreConverted(out);
    }

    private void assertRouteRouteTypesAreConverted(File out) throws IOException {
        List<String> routeLines = IOUtils.readLines(new ByteArrayInputStream(ZipFileUtils.extractFileFromZipFile(new FileInputStream(out), "routes.txt").toByteArray()));
        routeLines.remove(0); // remove header
        List<String> transformedRouteTypes = routeLines.stream().map(routeLine -> routeLine.split(",")[4]).collect(Collectors.toList());
        Assert.assertTrue("Expected all route types to have been converted to google valid codes", transformedRouteTypes.stream().allMatch(routeType -> GoogleRouteTypeCode.fromCode(Integer.valueOf(routeType)) != null));
        Assert.assertEquals("200", transformedRouteTypes.get(0));
        Assert.assertEquals("201", transformedRouteTypes.get(1));
        Assert.assertEquals("200", transformedRouteTypes.get(2));
        Assert.assertEquals("1501", transformedRouteTypes.get(3));
    }

    private void assertStopVehicleTypesAreConverted(File out) throws IOException {
        List<String> stopLines = IOUtils.readLines(new ByteArrayInputStream(ZipFileUtils.extractFileFromZipFile(new FileInputStream(out), "stops.txt").toByteArray()));
        stopLines.remove(0); // remove header
        Assert.assertTrue("Line without vehicle journey should not be changed", stopLines.get(0).endsWith(","));
        Assert.assertTrue("Line with valid value 701 should be kept", stopLines.get(1).endsWith(",701"));
        Assert.assertTrue("Line with extended value 1012 should be converted to 1000", stopLines.get(2).endsWith(",1000"));
        Assert.assertTrue("Line with extended value 1601 should be converted to 1700 (default)", stopLines.get(3).endsWith(",1700"));
        List<String> feedInfoLines = IOUtils.readLines(new ByteArrayInputStream(ZipFileUtils.extractFileFromZipFile(new FileInputStream(out), "feed_info.txt").toByteArray()));
        Assert.assertEquals("Feed info should be unchanged", "RB,Rutebanken,http://www.rutebanken.org,no", feedInfoLines.get(1));
    }

}
