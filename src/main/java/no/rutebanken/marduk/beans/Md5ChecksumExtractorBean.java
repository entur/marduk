package no.rutebanken.marduk.beans;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Bean to extract md5 checksum from files. If file ends with .md5 pick first token in file, otherwise compute sum from whole file.
 */
public class Md5ChecksumExtractorBean {

    private static Logger logger = LoggerFactory.getLogger(Md5ChecksumExtractorBean.class);

    public String extractMd5(byte[] data, Exchange exchange) {
        logger.debug("Entered extractMd5 method.");

        Optional<String> optionalFileName = Optional.ofNullable(exchange.getIn().getHeader(Exchange.FILE_NAME_ONLY, String.class));

        String fileName = optionalFileName.orElseThrow(() -> new IllegalStateException("Missing " + Exchange.FILE_NAME_ONLY + " header."));
        logger.debug("File name is: " + fileName);

        if (fileName.endsWith(".md5")) {
            return new String(data).split(" ")[0];
        } else {
            return org.apache.commons.codec.digest.DigestUtils.md5Hex(data);
        }
    }

}