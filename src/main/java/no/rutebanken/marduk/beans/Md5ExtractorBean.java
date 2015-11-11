package no.rutebanken.marduk.beans;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class Md5ExtractorBean {

    private static Logger logger = LoggerFactory.getLogger(Md5ExtractorBean.class);

    private static final String CAMEL_FILE_NAME_ONLY = "CamelFileNameOnly";

    public String extractMd5(byte[] data, Exchange exchange) {
        logger.debug("Entered extractMd5 method.");

        Optional<String> optionalFileName = Optional.ofNullable(exchange.getIn().getHeader(CAMEL_FILE_NAME_ONLY, String.class));

        String fileName = optionalFileName.orElseThrow(() -> new IllegalStateException("Missing " + CAMEL_FILE_NAME_ONLY + " header."));
        logger.debug("File name is: " + fileName);

        if (fileName.endsWith(".md5")) {
            return new String(data).split(" ")[0];
        } else if (fileName.endsWith(".pbf")) {
            return org.apache.commons.codec.digest.DigestUtils.md5Hex(data);
        } else {
            throw new IllegalStateException("Invalid filename '" + fileName + "'. Was expecting a file name ending with [.md5|.pbf].");
        }
    }

}