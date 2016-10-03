package no.rutebanken.marduk.routes.chouette;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChouetteUtilsTest {

    @Test
    public void testGetJobId(){
        String locationUrl = "http://localhost:8180/chouette_iev/referentials/avinor/scheduled_jobs/2321";
        assertEquals(Long.valueOf(2321), ChouetteUtils.getJobIdFromLocationUrl(locationUrl));
    }

    @Test (expected = IllegalArgumentException.class)
    public void testGetJobIdWithNull(){
        ChouetteUtils.getJobIdFromLocationUrl(null);
    }

    @Test
    public void testGetHttp4(){
        String url = "http://localhost:8180/chouette_iev/referentials/avinor";
        assertEquals("http4://localhost:8180/chouette_iev/referentials/avinor", ChouetteUtils.getHttp4(url));
    }

    @Test (expected = IllegalArgumentException.class)
    public void testGetHttp4WithNull(){
        ChouetteUtils.getHttp4(null);
    }


}
