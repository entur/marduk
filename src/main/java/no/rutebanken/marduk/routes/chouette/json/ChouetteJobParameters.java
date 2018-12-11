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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

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
			StringWriter writer = new StringWriter();
			mapper.writeValue(writer, this);
			String importJson = writer.toString();

			if (enableValidation) {

				// From here: Hack to inject validation json from file
				JSONParser p = new JSONParser();
				// Parse original JSON
				JSONObject importRoot = (JSONObject) p.parse(importJson);

				// Parse static validation json
				JSONObject validation = (JSONObject) p.parse(new InputStreamReader(
						this.getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/validation.json")));

				// Find root object in original json
				JSONObject object = (JSONObject) importRoot.get("parameters");
				// Add the "validation" part
			 	object.put("validation", validation);

				// Convert to string
				return importRoot.toJSONString();
			} else {
				return importJson;
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}


}
