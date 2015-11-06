package no.rutebanken.marduk.beans;

public class Md5ExtractorBean {

    public String extractMd5(String body) {
        return body.split(" ")[0];
    }

}