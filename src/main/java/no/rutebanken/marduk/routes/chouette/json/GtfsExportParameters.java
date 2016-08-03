package no.rutebanken.marduk.routes.chouette.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GtfsExportParameters {

    public Parameters parameters;

    public GtfsExportParameters(Parameters parameters) {
        this.parameters = parameters;
    }

    public static class Parameters {

        @JsonProperty("gtfs-export")
        public GtfsExport gtfsExport;

        public Parameters(GtfsExport gtfsExport) {
            this.gtfsExport = gtfsExport;
        }

    }

    public static class GtfsExport {

        public String name;

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

        @JsonProperty("add_metadata")
        public boolean addMetadata;

        @JsonProperty("time_zone")
        public String timeZone = "Europe/Oslo";

        @JsonProperty("route_type_id_scheme")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String routeTypeIdScheme = "extended";


        public GtfsExport(String name, String objectIdPrefix, String referentialName, String organisationName, String userName) {
            this.name = name;
            this.objectIdPrefix = objectIdPrefix;
            this.referentialName = referentialName;
            this.organisationName = organisationName;
            this.userName = userName;
        }

    }


}
