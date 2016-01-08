package no.rutebanken.marduk.management;

public class Provider {

    private final Long id;
    private final String name;
    private final String sftpAccount;
    private final ChouetteInfo chouetteInfo;

    public Provider(Long id, String name, String sftpAccount, ChouetteInfo chouetteInfo) {
        this.id = id;
        this.name = name;
        this.sftpAccount = sftpAccount;
        this.chouetteInfo = chouetteInfo;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSftpAccount() {
        return sftpAccount;
    }

    public ChouetteInfo getChouetteInfo() {
        return chouetteInfo;
    }

    @Override
    public String toString() {
        return "Provider{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", sftpAccount='" + sftpAccount + '\'' +
                ", chouetteInfo=" + chouetteInfo +
                '}';
    }
}
