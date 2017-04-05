package no.rutebanken.marduk.routes.chouette.json.exporter;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NetexExportParameters {
	public NetexExportParameters.Parameters parameters;

	public NetexExportParameters(NetexExportParameters.Parameters parameters) {
		this.parameters = parameters;
	}

	public static class Parameters {

		@JsonProperty("netexprofile-export")
		public NetexExport netexExport;

		public Parameters(NetexExport netexExport) {
			this.netexExport = netexExport;
		}

	}

	public static class NetexExport extends AbstractExportParameters {

		@JsonProperty("projection_type")
		public String projectionType;

		public NetexExport(String name, String referentialName, String organisationName, String userName, String projectionType) {
			this.name = name;
			this.projectionType = projectionType;
			this.referentialName = referentialName;
			this.organisationName = organisationName;
			this.userName = userName;
			this.startDate = DateUtils.startDateFor(2L);
		}

	}
}
