package no.rutebanken.marduk.routes.chouette.json.importer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import no.rutebanken.marduk.routes.chouette.json.AbstractParameters;
import no.rutebanken.marduk.routes.chouette.json.ChouetteJobParameters;

public class NetexImportParameters extends ChouetteJobParameters {

    public Parameters parameters;

	static class Parameters {
        @JsonProperty("netexprofile-import")
        public Netex netexImport;
    }

    static class Netex extends AbstractImportParameters {
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


    public static NetexImportParameters create(String name, String referentialName, String organisationName, String userName, boolean cleanRepository, boolean enableValidation) {
        Netex netexImport = new Netex();
        netexImport.name = name;
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
