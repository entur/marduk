package no.rutebanken.marduk.beans;

public class Md5Bean {

    public String generateMd5(byte[] body) {
        return org.apache.commons.codec.digest.DigestUtils.md5Hex(body);
    }

}