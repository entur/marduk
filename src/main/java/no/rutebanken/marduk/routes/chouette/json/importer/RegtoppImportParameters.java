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

package no.rutebanken.marduk.routes.chouette.json.importer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import no.rutebanken.marduk.routes.chouette.json.AbstractParameters;
import no.rutebanken.marduk.routes.chouette.json.ChouetteJobParameters;

import java.util.Set;

public class RegtoppImportParameters extends ChouetteJobParameters {

    public Parameters parameters;

	static class Parameters {

		@JsonProperty("regtopp-import")
		public Regtopp regtoppImport;

	}

	static class Regtopp extends AbstractImportParameters {

		@JsonProperty("object_id_prefix")
		@JsonInclude(JsonInclude.Include.ALWAYS)
		private String objectIdPrefix;

		@JsonProperty("references_type")
		public String referencesType = "";

		@JsonProperty("version")
		public String version;

		@JsonProperty("coordinate_projection")
		@JsonInclude(JsonInclude.Include.ALWAYS)
		public String coordinateProjection;

		@JsonProperty("calendar_strategy")
		public String calendarStrategy;

		@JsonProperty("batch_parse")
		public boolean batchParse;
		
	}

	public static RegtoppImportParameters create(String name, String objectIdPrefix, String referentialName,
			String organisationName, String userName, String version, String coordinateProjection,
			String calendarStrategy, boolean cleanRepository, boolean enableValidation, boolean allowCreateMissingStopPlace,
			                                            boolean enableStopPlaceIdMapping, boolean keepObsoleteLines,
			                                            boolean batchParse, Set<String> generateMissingRouteSectionsForModes) {
		Regtopp regtoppImport = new Regtopp();
		regtoppImport.name = name;
		regtoppImport.objectIdPrefix = objectIdPrefix;
		regtoppImport.referentialName = referentialName;
		regtoppImport.organisationName = organisationName;
		regtoppImport.userName = userName;
		regtoppImport.version = version; // R11D, R12, R12N, R13A
		regtoppImport.coordinateProjection = coordinateProjection; // EPSG:32632
																	// (UTM32_N)
		regtoppImport.calendarStrategy = calendarStrategy;
		regtoppImport.cleanRepository = cleanRepository ? "1" : "0";
		regtoppImport.keepObsoleteLines = keepObsoleteLines;
		regtoppImport.batchParse = batchParse;
		regtoppImport.stopAreaRemoteIdMapping = enableStopPlaceIdMapping;
		regtoppImport.generateMissingRouteSectionsForModes= generateMissingRouteSectionsForModes;
		if (allowCreateMissingStopPlace) {
			regtoppImport.stopAreaImportMode = AbstractImportParameters.StopAreaImportMode.CREATE_NEW;
		}
		
		Parameters parameters = new Parameters();
		parameters.regtoppImport = regtoppImport;
		RegtoppImportParameters regtoppImportParameters = new RegtoppImportParameters();
		regtoppImportParameters.parameters = parameters;
		regtoppImportParameters.enableValidation = enableValidation;
		return regtoppImportParameters;
	}

}
