package no.rutebanken.marduk.routes.chouette.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class NetexImportParameters extends ChouetteJobParameters{

    public Parameters parameters;

	static class Parameters {
        @JsonProperty("netexprofile-import")
        public NetexImport netexImport;
    }

    static class NetexImport extends AbstractImportParameters {
        @JsonProperty("profile_id")
        private String profileId = "norway";

        @JsonProperty("max_distance_for_commercial")
        public String maxDistanceForCommercial = "0";

        @JsonProperty("ignore_last_word")
        public String ignoreLastWord = "0";

        @JsonProperty("ignore_end_chars")
        public String ignoreEndChars = "0";

        @JsonProperty("max_distance_for_connection_link")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String maxDistanceForConnectionLink = "0";

    }


    public static NetexImportParameters create(String name, String objectIdPrefix, String referentialName, String organisationName, String userName, boolean cleanRepository, boolean enableValidation) {
        NetexImport netexImport = new NetexImport();
        netexImport.name = name;
        netexImport.objectIdPrefix = objectIdPrefix.toUpperCase();
        netexImport.referentialName = referentialName;
        netexImport.organisationName = organisationName;
        netexImport.userName = userName;
        netexImport.cleanRepository = cleanRepository ? "1":"0";
        Parameters parameters = new Parameters();
        parameters.netexImport = netexImport;
        NetexImportParameters netexImportParameters = new NetexImportParameters();
        netexImportParameters.parameters = parameters;
        netexImportParameters.enableValidation = enableValidation;
        return netexImportParameters;
    }

}
