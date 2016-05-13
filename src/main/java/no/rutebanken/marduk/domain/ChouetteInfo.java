package no.rutebanken.marduk.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChouetteInfo {

    public Long id;
    public String prefix;
    public String dataSpace;
    public String organisation;
    public String user;
    public String regtoppVersion;
    public String regtoppCoordinateProjection;


//    public ChouetteInfo(){}

//     ChouetteInfo(String prefix, String dataSpace, String organisation, String user) {
//        this.prefix = prefix;
//        this.dataSpace = dataSpace;
//        this.organisation = organisation;
//        this.user = user;
//    }
//
//    public ChouetteInfo(Long id, String prefix, String dataSpace, String organisation, String user) {
//        this(prefix, dataSpace, organisation, user);
//        this.id = id;
//    }

    @Override
    public String toString() {
        return "ChouetteInfo{" +
                "id=" + id +
                ", prefix='" + prefix + '\'' +
                ", dataSpace='" + dataSpace + '\'' +
                ", organisation='" + organisation + '\'' +
                ", user='" + user + '\'' +
                ", regtoppVersion='" + regtoppVersion + '\'' +
                ", regtoppCoordinateProjection='" + regtoppCoordinateProjection + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ChouetteInfo that = (ChouetteInfo) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (prefix != null ? !prefix.equals(that.prefix) : that.prefix != null) return false;
        if (dataSpace != null ? !dataSpace.equals(that.dataSpace) : that.dataSpace != null) return false;
        if (organisation != null ? !organisation.equals(that.organisation) : that.organisation != null) return false;
        return user != null ? user.equals(that.user) : that.user == null;

    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (prefix != null ? prefix.hashCode() : 0);
        result = 31 * result + (dataSpace != null ? dataSpace.hashCode() : 0);
        result = 31 * result + (organisation != null ? organisation.hashCode() : 0);
        result = 31 * result + (user != null ? user.hashCode() : 0);
        return result;
    }

}
