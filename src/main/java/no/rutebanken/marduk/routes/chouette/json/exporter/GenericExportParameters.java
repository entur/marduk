package no.rutebanken.marduk.routes.chouette.json.exporter;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GenericExportParameters {

	public Parameters parameters;

	public GenericExportParameters(Parameters parameters) {
		this.parameters = parameters;
	}

	public static class Parameters {

		@JsonProperty("generic-export")
		public GenericExport genericExport;

		public Parameters(GenericExport genericExport) {
			this.genericExport = genericExport;
		}
	}

	public static class GenericExport extends AbstractExportParameters {

		
        @JsonProperty("dest_referential_name")
        public String destReferentialName;

		
		public GenericExport(String name, String referentialName, String organisationName, String userName, String destReferentialName) {
			this.name = name;
			this.referentialName = referentialName;
			this.organisationName = organisationName;
			this.userName = userName;
			this.startDate = DateUtils.startDateFor(2L);
			this.endDate = DateUtils.endDateFor(365L);
			this.destReferentialName = destReferentialName;
		}

	}

}
