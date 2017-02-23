package no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;

@Service
public class ElasticsearchCommandWriterService {


	public String write(Collection<ElasticsearchCommand> elasticsearchCommands) {
		try {
			StringWriter stringWriter = new StringWriter();
			ElasticsearchBulkCommandWriter bulkCommandWriter = new ElasticsearchBulkCommandWriter(stringWriter);
			bulkCommandWriter.write(elasticsearchCommands);
			return stringWriter.toString();
		} catch (IOException ioE) {
			throw new RuntimeException("Failed to write elasticsearch commands: " + ioE.getMessage(), ioE);
		}

	}

}
