package no.rutebanken.marduk.routes.chouette.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringWriter;

public class GtfsImportParameters {

    public Parameters parameters;

    static class Parameters {

        @JsonProperty("gtfs-import")
        public GtfsImport gtfsImport;

    }

    static class GtfsImport {

        public String name;

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

        @JsonProperty("max_distance_for_commercial")
        public String maxDistanceForCommercial = "0";

        @JsonProperty("ignore_last_word")
        public String ignoreLastWord = "0";

        @JsonProperty("ignore_end_chars")
        public String ignoreEndChars = "0";

        @JsonProperty("max_distance_for_connection_link")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String maxDistanceForConnectionLink = "0";

        @JsonProperty("references_type")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String referencesType = "";

        @JsonProperty("route_type_id_scheme")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String routeTypeIdScheme = "any";

    }

    public static GtfsImportParameters create(String name, String objectIdPrefix, String referentialName, String organisationName, String userName) {
        GtfsImport gtfsImport = new GtfsImport();
        gtfsImport.name = name;
        gtfsImport.objectIdPrefix = objectIdPrefix;
        gtfsImport.referentialName = referentialName;
        gtfsImport.organisationName = organisationName;
        gtfsImport.userName = userName;
        Parameters parameters = new Parameters();
        parameters.gtfsImport = gtfsImport;
        GtfsImportParameters gtfsImportParameters = new GtfsImportParameters();
        gtfsImportParameters.parameters = parameters;
        return gtfsImportParameters;
    }


    public String toJsonString() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            StringWriter writer = new StringWriter();
            mapper.writeValue(writer, this);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
