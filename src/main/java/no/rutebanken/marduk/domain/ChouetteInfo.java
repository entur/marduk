package no.rutebanken.marduk.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChouetteInfo {

    public Long id;
    public String xmlns;
    public String xmlnsurl;
    public String referential;
    public String organisation;
    public String user;
    public String regtoppVersion;
    public String regtoppCoordinateProjection;
    public String regtoppCalendarStrategy;
    public String dataFormat;
    public boolean enableValidation = false;
    public boolean allowCreateMissingStopPlace = false;
    public boolean enableStopPlaceIdMapping = false;
    public boolean enableCleanImport = false;
	public Long migrateDataToProvider; // Which dataspace to transfer data to when provider dataspace is valid

    public Long getMigrateDataToProvider() {
		return migrateDataToProvider;
	}

	@Override
    public String toString() {
        return "ChouetteInfo{" +
                "id=" + id +
                ", xmlns='" + xmlns + '\'' +
                ", xmlnsurl='" + xmlnsurl + '\'' +
                ", referential='" + referential + '\'' +
                ", organisation='" + organisation + '\'' +
                ", user='" + user + '\'' +
                ", regtoppVersion='" + regtoppVersion + '\'' +
                ", regtoppCoordinateProjection='" + regtoppCoordinateProjection + '\'' +
                ", regtoppCalendarStrategy='" + regtoppCalendarStrategy + '\'' +
                ", dataFormat='" + dataFormat + '\'' +
                ", enableValidation='" + enableValidation + '\'' +
                ", allowCreateMissingStopPlace='" + allowCreateMissingStopPlace + '\'' +
                ", enableStopPlaceIdMapping='" + enableStopPlaceIdMapping + '\'' +
                ", enableCleanImport='" + enableCleanImport + '\'' +
                ", migrateDataToProvider='" + migrateDataToProvider + '\'' +
                '}';
    }

    public boolean usesRegtopp(){
        return "regtopp".equals(dataFormat);
    }

}
