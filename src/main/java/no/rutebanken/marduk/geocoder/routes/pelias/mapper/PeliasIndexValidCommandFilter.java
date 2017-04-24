package no.rutebanken.marduk.geocoder.routes.pelias.mapper;

import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PeliasIndexValidCommandFilter {
	private final Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * Remove invalid indexing commands.
	 * <p>
	 * Certain commands will be acceptable for insert into Elasticsearch, but will cause Pelias API to fail upon subsequent queries.
	 */
	public List<ElasticsearchCommand> removeInvalidCommands(Collection<ElasticsearchCommand> commands) {
		return commands.stream().filter(c -> isValid(c)).collect(Collectors.toList());
	}

	boolean isValid(ElasticsearchCommand command) {
		if (command == null || command.getIndex() == null) {
			logger.warn("Removing invalid command");
			return false;
		}
		if (command.getIndex().getIndex() == null || command.getIndex().getType() == null) {
			logger.warn("Removing invalid command with missing index name or type:" + command);
			return false;
		}

		if (!(command.getSource() instanceof PeliasDocument)) {
			logger.warn("Removing invalid command with missing pelias document:" + command);
			return false;
		}

		PeliasDocument doc = (PeliasDocument) command.getSource();

		if (doc.getLayer() == null || doc.getSource() == null || doc.getSourceId() == null) {
			logger.warn("Removing invalid command where pelias document is missing mandatory fields:" + command);
			return false;
		}

		if (doc.getCenterPoint() == null) {
			logger.debug("Removing invalid command where geometry is missing:" + command);
			return false;
		}

		return true;
	}

}
