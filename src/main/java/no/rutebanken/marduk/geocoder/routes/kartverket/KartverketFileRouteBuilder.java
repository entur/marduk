package no.rutebanken.marduk.geocoder.routes.kartverket;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;

import static no.rutebanken.marduk.Constants.*;

@Component
public class KartverketFileRouteBuilder extends BaseRouteBuilder {
	@Autowired
	private IdempotentRepository digestIdempotentRepository;

	@Override
	public void configure() throws Exception {
		super.configure();


		from("direct:uploadUpdatedFiles")
				.bean("kartverketService", "downloadFiles")
				.split().body().aggregationStrategy((oldExchange, newExchange) -> {
					if (newExchange.getIn().getHeader(CONTENT_CHANGED, false, Boolean.class)) {
						return newExchange;
					}
				return oldExchange;
				})
				.to("direct:uploadFileIfUpdated")
				.routeId("upload-updated-files");

		from("direct:uploadFileIfUpdated")
				.setHeader(Exchange.FILE_NAME, simple(("${body.name}")))
				.setHeader(FILE_HANDLE, simple("${header." + FOLDER_NAME + "}/${body.name}"))
				.process(e -> e.getIn().setHeader("file_digest", DigestUtils.md5Hex(e.getIn().getBody(InputStream.class))))
				.idempotentConsumer(header("file_digest")).messageIdRepository(digestIdempotentRepository)
				.log(LoggingLevel.INFO, "Uploading ${header." + FILE_HANDLE + "}")
				.to("direct:uploadBlob")
				.setHeader(CONTENT_CHANGED, constant(true))
				.end()
				.routeId("upload-file-if-updated");
	}
}
