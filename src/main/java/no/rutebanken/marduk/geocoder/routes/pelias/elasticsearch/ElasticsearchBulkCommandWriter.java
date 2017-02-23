package no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;


public class ElasticsearchBulkCommandWriter {

	private Writer writer;
	private ObjectMapper mapper;

	public ElasticsearchBulkCommandWriter(Writer writer) {
		this.writer = writer;
		mapper = new ObjectMapper();
		mapper.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

	}

	public void write(ElasticsearchCommand elasticsearchCommand) throws IOException {
		mapper.writeValue(writer, elasticsearchCommand);
		writer.append("\n");

		if (elasticsearchCommand.getSource() != null) {
			mapper.writeValue(writer, elasticsearchCommand.getSource());
			writer.append("\n");
		}
	}

	public void write(Collection<ElasticsearchCommand> elasticsearchCommands) throws IOException {
		for (ElasticsearchCommand elasticsearchCommand : elasticsearchCommands) {
			write(elasticsearchCommand);
		}

	}


}
