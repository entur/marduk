package no.rutebanken.marduk.routes.sftp;

import no.rutebanken.marduk.beans.FileTypeClassifierBean;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ValidationException;
import org.springframework.stereotype.Component;

/**
 * Downloads file from lamassu
 */
@Component
public class SftpRoute extends BaseRouteBuilder {

    @Override
    public void configure() throws Exception {
        super.configure();

        onException(ValidationException.class)
                .handled(true)
                .to("activemq:queue:DeadLetterQueue");

        from("sftp://nvdb@lamassu:22?privateKeyFile=/opt/jboss/.ssh/lamassu.pem&delay=30s&delete=true&localWorkDirectory=target/files/tmp")
                .log("Received file on route. Storing file...")
                .to("file:target/files/input/nvdb?fileName=${date:now:yyyyMMddHHmmss}-${file:name}")
                .log("Stored file. Posting file handle to queue...")
                .validate().method(FileTypeClassifierBean.class, "validateFile")
                .choice()
                    .when(header("filetype").isEqualTo("GTFS"))
                        .to("activemq:queue:ProcessGtfsQueue")
                        .log("Posted file on queue.")
                    .when(header("filename").isEqualTo("UNKNOWN"))
                        .log(LoggingLevel.INFO, "File ${header.CamelFileNameOnly} is of an unknown type. Could not process")
                    .otherwise()
                        .log(LoggingLevel.WARN, "Could not process file ${header.CamelFileNameOnly}");

    }

}
