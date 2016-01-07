package no.rutebanken.marduk.management;

public class Provider {

    private Long id;
    private String name;
    private String sftpDirectory;
    private String chouettePrefix;
    private String chouetteDataSpace;
    private String chouetteOrganisation;


    public Provider(Long id, String name, String sftpDirectory, String chouettePrefix, String chouetteDataSpace, String chouetteOrganisation) {
        this.id = id;
        this.name = name;
        this.sftpDirectory = sftpDirectory;
        this.chouettePrefix = chouettePrefix;
        this.chouetteDataSpace = chouetteDataSpace;
        this.chouetteOrganisation = chouetteOrganisation;
    }

    @Override
    public String toString() {
        return "Provider{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", sftpDirectory='" + sftpDirectory + '\'' +
                ", chouettePrefix='" + chouettePrefix + '\'' +
                ", chouetteDataSpace='" + chouetteDataSpace + '\'' +
                ", chouetteOrganisation='" + chouetteOrganisation + '\'' +
                '}';
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSftpDirectory() {
        return sftpDirectory;
    }

    public void setSftpDirectory(String sftpDirectory) {
        this.sftpDirectory = sftpDirectory;
    }

    public String getChouettePrefix() {
        return chouettePrefix;
    }

    public void setChouettePrefix(String chouettePrefix) {
        this.chouettePrefix = chouettePrefix;
    }

    public String getChouetteDataSpace() {
        return chouetteDataSpace;
    }

    public void setChouetteDataSpace(String chouetteDataSpace) {
        this.chouetteDataSpace = chouetteDataSpace;
    }

    public String getChouetteOrganisation() {
        return chouetteOrganisation;
    }

    public void setChouetteOrganisation(String chouetteOrganisation) {
        this.chouetteOrganisation = chouetteOrganisation;
    }
}
