package no.rutebanken.marduk.routes.chouette.json;

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

    public static class NeptuneExport {

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

        // TODO add neptune extensions
        
        public NeptuneExport(String name, String objectIdPrefix, String referentialName, String organisationName, String userName) {
            this.name = name;
            this.objectIdPrefix = objectIdPrefix;
            this.referentialName = referentialName;
            this.organisationName = organisationName;
            this.userName = userName;
        }

    }


}
