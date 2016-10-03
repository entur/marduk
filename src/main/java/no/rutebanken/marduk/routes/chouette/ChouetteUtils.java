package no.rutebanken.marduk.routes.chouette;

public class ChouetteUtils {

    public static String getHttp4(String url) {
        if (url == null) {
            throw new IllegalArgumentException("Url is null");
        }
        return url.replaceFirst("http", "http4");
    }

    public static Long getJobIdFromLocationUrl(String url) {
        if (url == null) {
            throw new IllegalArgumentException("Url is null");
        }
        return Long.valueOf(url.substring(url.lastIndexOf('/') + 1, url.length()));
    }
}
