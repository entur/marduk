package no.rutebanken.marduk.routes.chouette.json.exporter;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TransferExportParameters {

	public Parameters parameters;

	public TransferExportParameters(Parameters parameters) {
		this.parameters = parameters;
	}

	public static class Parameters {

		@JsonProperty("transfer-export")
		public TransferExport transferExport;

		public Parameters(TransferExport transferExport) {
			this.transferExport = transferExport;
		}
	}

	public static class TransferExport extends AbstractExportParameters {

		
        @JsonProperty("dest_referential_name")
        public String destReferentialName;

		
		public TransferExport(String name, String referentialName, String organisationName, String userName, String destReferentialName) {
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
