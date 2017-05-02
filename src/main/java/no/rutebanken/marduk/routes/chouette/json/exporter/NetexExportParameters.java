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
        @JsonProperty("valid_codespaces")
        private String validCodespaces;

        @JsonProperty("add_extension")
        private boolean addExtension = false;

        @JsonProperty("export_stops")
        public boolean exportStops = false;

        public NetexExport(String name, String referentialName, String organisationName, String validCodespaces, String userName, String projectionType, boolean exportStops) {
            this.name = name;
            this.projectionType = projectionType;
            this.referentialName = referentialName;
            this.organisationName = organisationName;
            this.userName = userName;
            this.validCodespaces = validCodespaces;
            this.startDate = DateUtils.startDateFor(2L);
            this.exportStops = exportStops;
        }

    }
}
