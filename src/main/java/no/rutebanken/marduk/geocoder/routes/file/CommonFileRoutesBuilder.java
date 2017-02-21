package no.rutebanken.marduk.geocoder.routes.file;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

import java.io.File;

import static org.apache.commons.io.FileUtils.deleteDirectory;

@Component
public class CommonFileRoutesBuilder extends BaseRouteBuilder {

	@Override
	public void configure() throws Exception {
		super.configure();

		from("direct:cleanUpLocalDirectory")
				.log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "Deleting local directory ${property." + Exchange.FILE_PARENT + "} ...")
				.process(e -> deleteDirectory(new File(e.getIn().getHeader(Exchange.FILE_PARENT, String.class))))
				.log(LoggingLevel.DEBUG, getClass().getName(), correlation() + "Local directory ${property." + Exchange.FILE_PARENT + "} cleanup done.")
				.routeId("cleanup-local-dir");
	}
}
