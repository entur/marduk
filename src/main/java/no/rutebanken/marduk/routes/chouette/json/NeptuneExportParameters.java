package no.rutebanken.marduk.routes.chouette.json;

import org.joda.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class NeptuneExportParameters {

	public Parameters parameters;

	public NeptuneExportParameters(Parameters parameters) {
		this.parameters = parameters;
	}

	public static class Parameters {

		@JsonProperty("neptune-export")
		public NeptuneExport neptuneExport;

		public Parameters(NeptuneExport neptuneExport) {
			this.neptuneExport = neptuneExport;
		}
	}

	public static class NeptuneExport extends AbstractImportParameters {

		@JsonProperty("projection_type")
		public String projectionType = "4326"; // WGS84

		@JsonProperty("references_type")
		@JsonInclude(JsonInclude.Include.ALWAYS)
		public String referencesType = null;

		@JsonProperty("reference_ids")
		@JsonInclude(JsonInclude.Include.ALWAYS)
		public String[] referenceIds = new String[0];

		@JsonProperty("add_extension")
		public String addExtension = "1";

		@JsonProperty("start_date")
		public String startDate = null;

		@JsonProperty("end_date")
		public String endDate = null;

		public NeptuneExport(String name, String referentialName, String organisationName, String userName) {
			this.name = name;

			this.referentialName = referentialName;
			this.organisationName = organisationName;
			this.userName = userName;

			LocalDate today = new LocalDate();

			startDate = today.minusDays(2).toString();
			endDate = today.plusYears(1).toString();

		}

	}

}
