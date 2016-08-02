package no.rutebanken.marduk.routes.chouette.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RegtoppImportParameters extends ChouetteImportParameters {

    public Parameters parameters;

	static class Parameters {

		@JsonProperty("regtopp-import")
		public RegtoppImport regtoppImport;

	}

	static class RegtoppImport {

		public String name;

		@JsonProperty("clean_repository")
		public String cleanRepository = "0";

		@JsonProperty("no_save")
		public String noSave = "0";

		@JsonProperty("user_name")
		public String userName;

		@JsonProperty("organisation_name")
		public String organisationName;

		@JsonProperty("referential_name")
		public String referentialName;

		@JsonProperty("object_id_prefix")
		public String objectIdPrefix;

		@JsonProperty("references_type")
		@JsonInclude(JsonInclude.Include.ALWAYS)
		public String referencesType = "";

		@JsonProperty("version")
		public String version;

		@JsonProperty("coordinate_projection")
		@JsonInclude(JsonInclude.Include.ALWAYS)
		public String coordinateProjection;

		@JsonProperty("calendar_strategy")
		public String calendarStrategy;

	}

	public static RegtoppImportParameters create(String name, String objectIdPrefix, String referentialName,
			String organisationName, String userName, String version, String coordinateProjection,
			String calendarStrategy, boolean cleanRepository, boolean enableValidation) {
		RegtoppImport regtoppImport = new RegtoppImport();
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
		Parameters parameters = new Parameters();
		parameters.regtoppImport = regtoppImport;
		RegtoppImportParameters regtoppImportParameters = new RegtoppImportParameters();
		regtoppImportParameters.parameters = parameters;
		regtoppImportParameters.enableValidation = enableValidation;
		return regtoppImportParameters;
	}

}
