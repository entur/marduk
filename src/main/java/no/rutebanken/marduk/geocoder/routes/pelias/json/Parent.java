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

package no.rutebanken.marduk.geocoder.routes.pelias.json;

import com.fasterxml.jackson.annotation.*;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;

@JsonRootName("parent")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Parent {

	@JsonProperty("country")
	private List<String> countryList;
	@JsonProperty("county")
	private List<String> countyList;
	@JsonProperty("postalCode")
	private List<String> postalCodeList;
	@JsonProperty("localadmin")
	private List<String> localadminList;
	@JsonProperty("locality")
	private List<String> localityList;
	@JsonProperty("borough")
	private List<String> boroughList;

	@JsonProperty("country_id")
	private List<String> countryIdList;
	@JsonProperty("county_id")
	private List<String> countyIdList;
	@JsonProperty("postalCode_id")
	private List<String> postalCodeIdList;
	@JsonProperty("localadmin_id")
	private List<String> localadminIdList;
	@JsonProperty("locality_id")
	private List<String> localityIdList;
	@JsonProperty("borough_id")
	private List<String> boroughIdList;

	public Parent() {
	}

	@JsonIgnore
	public String getCountry() {
		return getFirst(countryList);
	}
	@JsonIgnore
	public String getCounty() {
		return getFirst(countyList);
	}
	@JsonIgnore
	public String getPostalCode() {
		return getFirst(postalCodeList);
	}
	@JsonIgnore
	public String getLocaladmin() {
		return getFirst(localadminList);
	}
	@JsonIgnore
	public String getLocality() {
		return getFirst(localityList);
	}
	@JsonIgnore
	public String getCountryId() {
		return getFirst(countryIdList);
	}
	@JsonIgnore
	public String getCountyId() {
		return getFirst(countyIdList);
	}

	@JsonIgnore
	public String getPostalCodeId() {
		return getFirst(postalCodeIdList);
	}
	@JsonIgnore
	public String getLocaladminId() {
		return getFirst(localadminIdList);
	}
	@JsonIgnore
	public String getLocalityId() {
		return getFirst(localityIdList);
	}
	@JsonIgnore
	public String getBorough() {
		return getFirst(boroughList);
	}
	@JsonIgnore
	public String getBoroughId() {
		return getFirst(boroughIdList);
	}

	public void setCountry(String country) {
		this.countryList = asList(country);
	}

	public void setCounty(String county) {
		this.countyList = asList(county);
	}

	public void setPostalCode(String postalCode) {
		this.postalCodeList = asList(postalCode);
	}

	public void setLocaladmin(String localadmin) {
		this.localadminList = asList(localadmin);
	}

	public void setLocality(String locality) {
		this.localityList = asList(locality);
	}

	public void setBorough(String borough) {
		this.boroughList = asList(borough);
	}

	public void setCountryId(String countryId) {
		this.countryIdList = asList(countryId);
	}

	public void setCountyId(String countyId) {
		this.countyIdList = asList(countyId);
	}

	public void setPostalCodeId(String postalCodeId) {
		this.postalCodeIdList = asList(postalCodeId);
	}

	public void setLocaladminId(String localadminId) {
		this.localadminIdList = asList(localadminId);
	}

	public void setLocalityId(String localityId) {
		this.localityIdList = asList(localityId);
	}

	public void setBoroughId(String boroughId) {
		this.boroughIdList = asList(boroughId);
	}

	private <T> List<T> asList(T obj) {
		return obj == null ? null : Arrays.asList(obj);
	}

	private <T> T getFirst(List<T> list) {
		return CollectionUtils.isEmpty(list) ? null : list.get(0);
	}

	public static Parent.Builder builder() {
		return new Parent.Builder();
	}


	public static class Builder {

		protected Parent parent = new Parent();

		private Builder() {
		}


		public Builder withCountry(String country) {
			parent.setCounty(country);
			return this;
		}

		public Builder withPostalCode(String postalCode) {

			parent.setPostalCode(postalCode);
			return this;
		}

		public Builder withLocaladmin(String localadmin) {
			parent.setLocaladmin(localadmin);
			return this;
		}

		public Builder withLocality(String locality) {
			parent.setLocality(locality);
			return this;
		}

		public Builder withCounty(String county) {
			parent.setCounty(county);
			return this;
		}


		public Builder withBorough(String borough) {
			parent.setBorough(borough);
			return this;
		}

		public Builder withCountryId(String countryId) {
			parent.setCountryId(countryId);
			return this;
		}

		public Builder withPostalCodeId(String postalCodeId) {
			parent.setPostalCodeId(postalCodeId);
			return this;
		}

		public Builder withLocaladminId(String localadminId) {
			parent.setLocaladminId(localadminId);
			return this;
		}

		public Builder withLocalityId(String localityId) {
			parent.setLocalityId(localityId);
			return this;
		}

		public Builder withCountyId(String countyId) {
			parent.setCountyId(countyId);
			return this;
		}

		public Builder withBoroughId(String boroughId) {
			parent.setBoroughId(boroughId);
			return this;
		}

		public Parent build() {
			return parent;
		}
	}


}
