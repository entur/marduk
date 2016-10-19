package no.rutebanken.marduk;

import org.opentripplanner.common.MavenVersion;

public class Utils {

    public static String getHttp4(String url) {
        if (url == null) {
            throw new IllegalArgumentException("Url is null");
        }
        return url.replaceFirst("http", "http4");
    }

    public static Long getLastPathElementOfUrl(String url) {
        if (url == null) {
            throw new IllegalArgumentException("Url is null");
        }
        return Long.valueOf(url.substring(url.lastIndexOf('/') + 1, url.length()));
    }

    public static String getOtpVersion(){
        return MavenVersion.VERSION.version;
    }
}
