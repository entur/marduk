package no.rutebanken.marduk.routes.chouette.json.importer;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonProperty;

import no.rutebanken.marduk.routes.chouette.json.ChouetteJobParameters;

public class NetexImportParameters extends ChouetteJobParameters {

    public Parameters parameters;

	static class Parameters {
        @JsonProperty("netexprofile-import")
        public Netex netexImport;
    }

    static class Netex extends AbstractImportParameters {

        @JsonProperty("valid_codespaces")
        public String validCodespaces;

    }


    public static NetexImportParameters create(String name, String referentialName, String organisationName, String userName, boolean cleanRepository, boolean enableValidation, boolean enableStopPlaceUpdate, String xmlns, String xmlnsurl) {
        Netex netexImport = new Netex();
        netexImport.name = name;
        netexImport.referentialName = referentialName;
        netexImport.organisationName = organisationName;
        netexImport.userName = userName;
        netexImport.cleanRepository = cleanRepository ? "1":"0";
        netexImport.validCodespaces = StringUtils.join(new String[] {xmlns,xmlnsurl},",");
        netexImport.updateStopPlaces = enableStopPlaceUpdate;
        Parameters parameters = new Parameters();
        parameters.netexImport = netexImport;
        NetexImportParameters netexImportParameters = new NetexImportParameters();
        netexImportParameters.parameters = parameters;
        netexImportParameters.enableValidation = enableValidation;
        return netexImportParameters;
    }

}
