package no.rutebanken.marduk.routes.chouette.json.exporter;

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

	public static class NeptuneExport extends AbstractExportParameters {

		@JsonProperty("projection_type")
		public String projectionType = "4326"; // WGS84

		@JsonProperty("add_extension")
		public String addExtension = "1";

		public NeptuneExport(String name, String referentialName, String organisationName, String userName) {
			this.name = name;
			this.referentialName = referentialName;
			this.organisationName = organisationName;
			this.userName = userName;
			this.startDate = DateUtils.startDateFor(2L);
		}

	}

}
