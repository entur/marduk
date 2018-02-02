/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

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
