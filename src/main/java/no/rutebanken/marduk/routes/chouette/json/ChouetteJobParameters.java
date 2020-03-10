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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;

public abstract class ChouetteJobParameters {

	@JsonIgnore
	public boolean enableValidation = false;

	@SuppressWarnings("unchecked")
	public String toJsonString() {
		try {
			ObjectMapper mapper = new ObjectMapper();
			if (enableValidation) {
				// insert the validation node into the parameters node of the JSON message.
				JsonNode importRootNode = mapper.valueToTree(this);
				JsonNode validationNode = mapper.readTree(new InputStreamReader(
						this.getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/validation.json")));

				((ObjectNode) (importRootNode).get("parameters")).set("validation", validationNode);
				return mapper.writeValueAsString(importRootNode);
			} else {
				StringWriter writer = new StringWriter();
				mapper.writeValue(writer, this);
				return writer.toString();
			}
		} catch (IOException e) {
			throw new MardukException(e);
		}

	}
}
