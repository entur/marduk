package no.rutebanken.marduk.routes.chouette.json.importer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import no.rutebanken.marduk.routes.chouette.json.AbstractParameters;
import no.rutebanken.marduk.routes.chouette.json.ChouetteJobParameters;

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
			String calendarStrategy, boolean cleanRepository, boolean enableValidation, boolean keepObsoleteLines, boolean batchParse) {
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
		
		Parameters parameters = new Parameters();
		parameters.regtoppImport = regtoppImport;
		RegtoppImportParameters regtoppImportParameters = new RegtoppImportParameters();
		regtoppImportParameters.parameters = parameters;
		regtoppImportParameters.enableValidation = enableValidation;
		return regtoppImportParameters;
	}

}
