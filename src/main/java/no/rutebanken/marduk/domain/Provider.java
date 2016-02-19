package no.rutebanken.marduk.domain;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;

@Entity
public class Provider {

    @Id
    public Long id;
    public String name;
    public String sftpAccount;
    @OneToOne
    public ChouetteInfo chouetteInfo;

    public Provider(){}

    public Provider(Long id, String name, String sftpAccount, ChouetteInfo chouetteInfo) {
        this.id = id;
        this.name = name;
        this.sftpAccount = sftpAccount;
        this.chouetteInfo = chouetteInfo;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Provider provider = (Provider) o;

        if (id != null ? !id.equals(provider.id) : provider.id != null) return false;
        if (name != null ? !name.equals(provider.name) : provider.name != null) return false;
        if (sftpAccount != null ? !sftpAccount.equals(provider.sftpAccount) : provider.sftpAccount != null)
            return false;
        return chouetteInfo != null ? chouetteInfo.equals(provider.chouetteInfo) : provider.chouetteInfo == null;

    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (sftpAccount != null ? sftpAccount.hashCode() : 0);
        result = 31 * result + (chouetteInfo != null ? chouetteInfo.hashCode() : 0);
        return result;
    }
}
