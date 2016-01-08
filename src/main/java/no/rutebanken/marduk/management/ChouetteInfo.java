package no.rutebanken.marduk.management;

public class ChouetteInfo {

    private final String prefix;
    private final String dataSpace;
    private final String organisation;
    private final String user;

    public ChouetteInfo(String prefix, String dataSpace, String organisation, String user) {
        this.prefix = prefix;
        this.dataSpace = dataSpace;
        this.organisation = organisation;
        this.user = user;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getDataSpace() {
        return dataSpace;
    }

    public String getOrganisation() {
        return organisation;
    }

    public String getUser() {
        return user;
    }

    @Override
    public String toString() {
        return "ChouetteInfo{" +
                "prefix='" + prefix + '\'' +
                ", dataSpace='" + dataSpace + '\'' +
                ", organisation='" + organisation + '\'' +
                ", user='" + user + '\'' +
                '}';
    }
}
