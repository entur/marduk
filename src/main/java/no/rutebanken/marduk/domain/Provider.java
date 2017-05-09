package no.rutebanken.marduk.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Provider {

    public Long id;
    
    public Long getId() {
    	return id;
    }
    public String name;
    public String sftpAccount;

    public ChouetteInfo getChouetteInfo() {
		return chouetteInfo;
	}
	public ChouetteInfo chouetteInfo;

    @Override
    public String toString() {
        return "Provider{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", sftpAccount='" + sftpAccount + '\'' +
                ", chouetteInfo=" + chouetteInfo +
                '}';
    }

    @JsonCreator
    public static Provider create(String jsonString) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(jsonString, Provider.class);
    }

}
