package no.rutebanken.marduk.routes.otp;

import org.junit.Test;

import java.io.IOException;
import java.util.Date;

import static org.junit.Assert.assertTrue;

public class MetadataTest {

    @Test
    public void getJson() throws IOException {
        String filename = "unknown_file_name_flag";

        Metadata.Status status = Metadata.Status.NOK;
        String json = new Metadata("OSM file update status.",
                        filename,
                        new Date(),
                        status,
                        Metadata.Action.OSM_NORWAY_UPDATED).getJson();
        assertTrue(json.contains("OSM"));
    }

}