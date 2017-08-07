package no.rutebanken.marduk.routes.chouette.json.importer;

import com.fasterxml.jackson.annotation.JsonProperty;
import no.rutebanken.marduk.routes.chouette.json.ChouetteJobParameters;

public class NetexImportParameters extends ChouetteJobParameters {

    public Parameters parameters;

	static class Parameters {
        @JsonProperty("netexprofile-import")
        public Netex netexImport;
    }

    static class Netex extends AbstractImportParameters {}

    public static NetexImportParameters create(String name, String referentialName, String organisationName, String userName, boolean cleanRepository, boolean enableValidation, boolean allowCreateMissingStopPlace, boolean enableStopPlaceIdMapping) {
        Netex netexImport = new Netex();
        netexImport.name = name;
        netexImport.referentialName = referentialName;
        netexImport.organisationName = organisationName;
        netexImport.userName = userName;
        netexImport.cleanRepository = cleanRepository ? "1":"0";
        netexImport.stopAreaRemoteIdMapping = enableStopPlaceIdMapping;
        if (allowCreateMissingStopPlace) {
            netexImport.stopAreaImportMode = AbstractImportParameters.StopAreaImportMode.CREATE_NEW;
        }
        Parameters parameters = new Parameters();
        parameters.netexImport = netexImport;
        NetexImportParameters netexImportParameters = new NetexImportParameters();
        netexImportParameters.parameters = parameters;
        netexImportParameters.enableValidation = enableValidation;
        return netexImportParameters;
    }

}
