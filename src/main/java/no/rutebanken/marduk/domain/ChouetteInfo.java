package no.rutebanken.marduk.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChouetteInfo {

    public Long id;
    public String prefix;
    public String referential;
    public String organisation;
    public String user;
    public String regtoppVersion;
    public String regtoppCoordinateProjection;
    public String regtoppCalendarStrategy;
    public String dataFormat;
    public boolean enableValidation = false;

    @Override
    public String toString() {
        return "ChouetteInfo{" +
                "id=" + id +
                ", prefix='" + prefix + '\'' +
                ", referential='" + referential + '\'' +
                ", organisation='" + organisation + '\'' +
                ", user='" + user + '\'' +
                ", regtoppVersion='" + regtoppVersion + '\'' +
                ", regtoppCoordinateProjection='" + regtoppCoordinateProjection + '\'' +
                ", regtoppCalendarStrategy='" + regtoppCalendarStrategy + '\'' +
                ", dataFormat='" + dataFormat + '\'' +
                ", enableValidation='" + enableValidation + '\'' +
                '}';
    }

    public boolean usesRegtopp(){
        return "regtopp".equals(dataFormat);
    }

}
