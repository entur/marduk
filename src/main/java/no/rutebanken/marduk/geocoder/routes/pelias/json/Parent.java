package no.rutebanken.marduk.geocoder.routes.pelias.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import java.util.ArrayList;
import java.util.List;

@JsonRootName("parent")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Parent {

	private List<String> country;
	private List<String> county;
	private List<String> postalCode;
	private List<String> localadmin;
	private List<String> locality;
	private List<String> borough;

	@JsonProperty("country_id")
	private List<String> countryId;
	@JsonProperty("county_id")
	private List<String> countyId;
	@JsonProperty("postalCode_id")
	private List<String> postalCodeId;
	@JsonProperty("localadmin_id")
	private List<String> localadminId;
	@JsonProperty("locality_id")
	private List<String> localityId;
	@JsonProperty("borough_id")
	private List<String> boroughId;

	public List<String> getCountry() {
		return country;
	}

	public void setCountry(List<String> country) {
		this.country = country;
	}

	public List<String> getCounty() {
		return county;
	}

	public void setCounty(List<String> county) {
		this.county = county;
	}

	public List<String> getPostalCode() {
		return postalCode;
	}

	public void setPostalCode(List<String> postalCode) {
		this.postalCode = postalCode;
	}

	public List<String> getLocaladmin() {
		return localadmin;
	}

	public void setLocaladmin(List<String> localadmin) {
		this.localadmin = localadmin;
	}

	public List<String> getlocality() {
		return locality;
	}

	public void setlocality(List<String> locality) {
		this.locality = locality;
	}

	public List<String> getCountryId() {
		return countryId;
	}

	public void setCountryId(List<String> countryId) {
		this.countryId = countryId;
	}

	public List<String> getCountyId() {
		return countyId;
	}

	public void setCountyId(List<String> countyId) {
		this.countyId = countyId;
	}

	public List<String> getPostalCodeId() {
		return postalCodeId;
	}

	public void setPostalCodeId(List<String> postalCodeId) {
		this.postalCodeId = postalCodeId;
	}

	public List<String> getLocaladminId() {
		return localadminId;
	}

	public void setLocaladminId(List<String> localadminId) {
		this.localadminId = localadminId;
	}

	public List<String> getlocalityId() {
		return localityId;
	}

	public void setlocalityId(List<String> localityId) {
		this.localityId = localityId;
	}

	public List<String> getBorough() {
		return borough;
	}

	public void setBorough(List<String> borough) {
		this.borough = borough;
	}

	public List<String> getBoroughId() {
		return boroughId;
	}

	public void setBoroughId(List<String> boroughId) {
		this.boroughId = boroughId;
	}

	public static Parent.Builder builder() {
		return new Parent.Builder();
	}

	public static class Builder {

		protected Parent parent = new Parent();

		private Builder() {
		}


		public Builder withCountry(String country) {
			if (parent.country == null) {
				parent.country = new ArrayList<>();
			}
			parent.country.add(country);
			return this;
		}

		public Builder withPostalCode(String postalCode) {
			if (parent.postalCode == null) {
				parent.postalCode = new ArrayList<>();
			}
			parent.postalCode.add(postalCode);
			return this;
		}

		public Builder withLocaladmin(String localadmin) {
			if (parent.localadmin == null) {
				parent.localadmin = new ArrayList<>();
			}
			parent.localadmin.add(localadmin);
			return this;
		}

		public Builder withlocality(String locality) {
			if (parent.locality == null) {
				parent.locality = new ArrayList<>();
			}
			parent.locality.add(locality);
			return this;
		}

		public Builder withCounty(String county) {
			if (parent.county == null) {
				parent.county = new ArrayList<>();
			}
			parent.county.add(county);
			return this;
		}


		public Builder withBorough(String borough) {
			if (parent.borough == null) {
				parent.borough = new ArrayList<>();
			}
			parent.borough.add(borough);
			return this;
		}

		public Builder withCountryId(String countryId) {
			if (parent.countryId == null) {
				parent.countryId = new ArrayList<>();
			}
			parent.countryId.add(countryId);
			return this;
		}

		public Builder withPostalCodeId(String postalCodeId) {
			if (parent.postalCodeId == null) {
				parent.postalCodeId = new ArrayList<>();
			}
			parent.postalCodeId.add(postalCodeId);
			return this;
		}

		public Builder withLocaladminId(String localadminId) {
			if (parent.localadminId == null) {
				parent.localadminId = new ArrayList<>();
			}
			parent.localadminId.add(localadminId);
			return this;
		}

		public Builder withlocalityId(String localityId) {
			if (parent.localityId == null) {
				parent.localityId = new ArrayList<>();
			}
			parent.localityId.add(localityId);
			return this;
		}

		public Builder withCountyId(String countyId) {
			if (parent.countyId == null) {
				parent.countyId = new ArrayList<>();
			}
			parent.countyId.add(countyId);
			return this;
		}

		public Builder withBoroughId(String boroughId) {
			if (parent.boroughId == null) {
				parent.boroughId = new ArrayList<>();
			}
			parent.boroughId.add(boroughId);
			return this;
		}

		public Parent build() {
			return parent;
		}
	}

}
