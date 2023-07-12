/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectReader;
import no.rutebanken.marduk.json.ObjectMapperFactory;

import java.io.IOException;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Provider {

    private static final ObjectReader OBJECT_READER = ObjectMapperFactory.getSharedObjectMapper().readerFor(Provider.class);

    private Long id;

    public Long getId() {
    	return id;
    }
    private String name;
    private String sftpAccount;

    public ChouetteInfo getChouetteInfo() {
		return chouetteInfo;
	}
	private ChouetteInfo chouetteInfo;

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
        return OBJECT_READER.readValue(jsonString);
    }

    public Provider setId(Long id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public Provider setName(String name) {
        this.name = name;
        return this;
    }

    public String getSftpAccount() {
        return sftpAccount;
    }

    public Provider setSftpAccount(String sftpAccount) {
        this.sftpAccount = sftpAccount;
        return this;
    }

    public Provider setChouetteInfo(ChouetteInfo chouetteInfo) {
        this.chouetteInfo = chouetteInfo;
        return this;
    }
}
