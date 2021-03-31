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

package no.rutebanken.marduk.routes.chouette.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.json.ObjectMapperFactory;

import java.io.IOException;
import java.io.InputStreamReader;

public abstract class ChouetteJobParameters {

	private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getSharedObjectMapper().copy();

	@JsonIgnore
	public boolean enableValidation = false;

	public String toJsonString() {
		try {
			if (enableValidation) {
				// insert the validation node into the parameters node of the JSON message.
				JsonNode importRootNode = OBJECT_MAPPER.valueToTree(this);
				JsonNode validationNode = OBJECT_MAPPER.readTree(new InputStreamReader(
						this.getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/validation.json")));

				((ObjectNode) (importRootNode).get("parameters")).set("validation", validationNode);
				return OBJECT_MAPPER.writeValueAsString(importRootNode);
			} else {
				return OBJECT_MAPPER.writeValueAsString(this);
			}
		} catch (IOException e) {
			throw new MardukException(e);
		}

	}
}
