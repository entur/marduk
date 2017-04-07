package no.rutebanken.marduk.routes.chouette.json.importer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import no.rutebanken.marduk.routes.chouette.json.ChouetteJobParameters;

public class GtfsImportParameters extends ChouetteJobParameters {

    public Parameters parameters;

	static class Parameters {

        @JsonProperty("gtfs-import")
        public Gtfs gtfsImport;

    }

    static class Gtfs extends AbstractImportParameters {

        @JsonProperty("object_id_prefix")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String objectIdPrefix;

    	@JsonProperty("split_id_on_dot")
        @JsonInclude(JsonInclude.Include.ALWAYS)
    	private String splitIdOnDot = "0";

    	@JsonProperty("max_distance_for_commercial")
        public String maxDistanceForCommercial = "0";

        @JsonProperty("ignore_last_word")
        public String ignoreLastWord = "0";

        @JsonProperty("ignore_end_chars")
        public String ignoreEndChars = "0";

        @JsonProperty("max_distance_for_connection_link")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String maxDistanceForConnectionLink = "0";

        @JsonProperty("route_type_id_scheme")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String routeTypeIdScheme = "any";

    }

    public static GtfsImportParameters create(String name, String objectIdPrefix, String referentialName, String organisationName, String userName, boolean cleanRepository,boolean enableValidation) {
        Gtfs gtfsImport = new Gtfs();
        gtfsImport.name = name;
        gtfsImport.objectIdPrefix = objectIdPrefix;
        gtfsImport.referentialName = referentialName;
        gtfsImport.organisationName = organisationName;
        gtfsImport.userName = userName;
        gtfsImport.cleanRepository = cleanRepository ? "1":"0";
        Parameters parameters = new Parameters();
        parameters.gtfsImport = gtfsImport;
        GtfsImportParameters gtfsImportParameters = new GtfsImportParameters();
        gtfsImportParameters.parameters = parameters;
        gtfsImportParameters.enableValidation = enableValidation;
        return gtfsImportParameters;
    }

}
