package no.rutebanken.marduk.geocoder.routes.kartverket;

import no.rutebanken.marduk.domain.FileNameAndDigest;
import no.rutebanken.marduk.geocoder.routes.util.MarkContentChangedAggregationStrategy;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.services.BlobStoreService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.*;
import static org.apache.camel.Exchange.FILE_PARENT;

@Component
public class KartverketFileRouteBuilder extends BaseRouteBuilder {
	@Autowired
	private IdempotentRepository idempotentDownloadRepository;

	@Value("${kartverket.download.directory:files/kartverket}")
	private String localDownloadDir;

	@Autowired
	private BlobStoreService blobStoreService;

	@Override
	public void configure() throws Exception {
		super.configure();

		from("direct:uploadUpdatedFiles")
				.setHeader(FILE_PARENT, simple(localDownloadDir + "/${date:now:yyyyMMddHHmmss}"))
				.doTry()
				.bean("kartverketService", "downloadFiles")
				.process(e -> deleteNoLongerActiveFiles(e))
				.to("direct:kartverketUploadOnlyUpdatedFiles")
				.doFinally()
				.to("direct:cleanUpLocalDirectory")
				.routeId("upload-updated-files");

		from("direct:kartverketUploadOnlyUpdatedFiles")
				.split().body().aggregationStrategy(new MarkContentChangedAggregationStrategy())
				.to("direct:kartverketUploadFileIfUpdated")
				.routeId("kartverket-upload-only--updated-files");


		from("direct:kartverketUploadFileIfUpdated")
				.setHeader(Exchange.FILE_NAME, simple(("${body.name}")))
				.setHeader(FILE_HANDLE, simple("${header." + FOLDER_NAME + "}/${body.name}"))
				.process(e -> e.getIn().setHeader("file_NameAndDigest", new FileNameAndDigest(e.getIn().getHeader(FILE_HANDLE, String.class),
						                                                                             DigestUtils.md5Hex(e.getIn().getBody(InputStream.class)))))
				.idempotentConsumer(header("file_NameAndDigest")).messageIdRepository(idempotentDownloadRepository)
				.log(LoggingLevel.INFO, "Uploading ${header." + FILE_HANDLE + "}")
				.to("direct:uploadBlob")
				.setHeader(CONTENT_CHANGED, constant(true))
				.end()
				.routeId("upload-file-if-updated");
	}

	private void deleteNoLongerActiveFiles(Exchange e) {
		List<File> activeFiles = e.getIn().getBody(List.class);
		Set<String> activeFileNames = activeFiles.stream().map(f -> f.getName()).collect(Collectors.toSet());
		BlobStoreFiles blobs = blobStoreService.listBlobsInFolder(e.getIn().getHeader(FOLDER_NAME, String.class), e);

		blobs.getFiles().stream().filter(b -> !activeFileNames.contains(Paths.get(b.getName()).getFileName().toString())).forEach(b -> deleteNoLongerActiveBlob(b, e));

	}

	private void deleteNoLongerActiveBlob(BlobStoreFiles.File blob, Exchange e) {
		log.info("Delete blob no longer part of Kartverekt dataset: " + blob);
		blobStoreService.deleteBlob(blob.getName(), e);
	}
}
