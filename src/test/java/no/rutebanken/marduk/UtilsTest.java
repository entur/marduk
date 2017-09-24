package no.rutebanken.marduk;

import org.apache.commons.codec.CharEncoding;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.Charset;

import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class UtilsTest {

    @Test
    public void testGetJobId(){
        String locationUrl = "http://localhost:8180/chouette_iev/referentials/avinor/scheduled_jobs/2321";
        assertEquals(Long.valueOf(2321), Utils.getLastPathElementOfUrl(locationUrl));
    }

    @Test (expected = IllegalArgumentException.class)
    public void testGetJobIdWithNull(){
        Utils.getLastPathElementOfUrl(null);
    }

    @Test
    public void testGetHttp4(){
        String url = "http://localhost:8180/chouette_iev/referentials/avinor";
        assertEquals("http4://localhost:8180/chouette_iev/referentials/avinor", Utils.getHttp4(url));
    }

    @Test (expected = IllegalArgumentException.class)
    public void testGetHttp4WithNull(){
        Utils.getHttp4(null);
    }

    @Test
    public void getOtpVersion(){
        assertThat(Utils.getOtpVersion(), matchesPattern("\\d+\\.\\d+\\.\\d+\\.RB.*"));
    }


    @Test
    public void tust(){
        Assert.assertFalse(Charset.forName( CharEncoding.ISO_8859_1 ).newEncoder().canEncode("sof-20170904121616-2907_20170904_Buss_og_ekspressbat_til_rutesøk_19.06.2017-28.02.2018 (1).zip"));
    }
}
