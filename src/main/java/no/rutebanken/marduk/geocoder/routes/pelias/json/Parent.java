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

	public List<String> getCounty() {
		return county;
	}

	public List<String> getPostalCode() {
		return postalCode;
	}

	public List<String> getLocaladmin() {
		return localadmin;
	}

	public List<String> getLocality() {
		return locality;
	}

	public List<String> getCountryId() {
		return countryId;
	}

	public List<String> getCountyId() {
		return countyId;
	}


	public List<String> getPostalCodeId() {
		return postalCodeId;
	}

	public List<String> getLocaladminId() {
		return localadminId;
	}

	public List<String> getLocalityId() {
		return localityId;
	}
	public List<String> getBorough() {
		return borough;
	}

	public List<String> getBoroughId() {
		return boroughId;
	}

	public static Parent.Builder builder() {
		return new Parent.Builder();
	}


	public void addCountry(String country) {
		if (country == null) {
			return;
		}
		if (this.country == null) {
			this.country = new ArrayList<>();
		}
		this.country.add(country);

	}

	public void addPostalCode(String postalCode) {
		if (postalCode == null) {
			return;
		}
		if (this.postalCode == null) {
			this.postalCode = new ArrayList<>();
		}
		this.postalCode.add(postalCode);

	}

	public void addLocaladmin(String localadmin) {
		if (localadmin == null) {
			return;
		}
		if (this.localadmin == null) {
			this.localadmin = new ArrayList<>();
		}
		this.localadmin.add(localadmin);

	}

	public void addLocality(String locality) {
		if (locality == null) {
			return;
		}
		if (this.locality == null) {
			this.locality = new ArrayList<>();
		}
		this.locality.add(locality);

	}

	public void addCounty(String county) {
		if (county == null) {
			return;
		}
		if (this.county == null) {
			this.county = new ArrayList<>();
		}
		this.county.add(county);

	}


	public void addBorough(String borough) {
		if (borough == null) {
			return;
		}
		if (this.borough == null) {
			this.borough = new ArrayList<>();
		}
		this.borough.add(borough);

	}

	public void addCountryId(String countryId) {
		if (countryId == null) {
			return;
		}
		if (this.countryId == null) {
			this.countryId = new ArrayList<>();
		}
		this.countryId.add(countryId);

	}

	public void addPostalCodeId(String postalCodeId) {
		if (postalCodeId == null) {
			return;
		}
		if (this.postalCodeId == null) {
			this.postalCodeId = new ArrayList<>();
		}
		this.postalCodeId.add(postalCodeId);

	}

	public void addLocaladminId(String localadminId) {
		if (localadminId == null) {
			return;
		}
		if (this.localadminId == null) {
			this.localadminId = new ArrayList<>();
		}
		this.localadminId.add(localadminId);

	}

	public void addLocalityId(String localityId) {
		if (localityId == null) {
			return;
		}
		if (this.localityId == null) {
			this.localityId = new ArrayList<>();
		}
		this.localityId.add(localityId);

	}

	public void addCountyId(String countyId) {
		if (countyId == null) {
			return;
		}
		if (this.countyId == null) {
			this.countyId = new ArrayList<>();
		}
		this.countyId.add(countyId);

	}

	public void addBoroughId(String boroughId) {
		if (boroughId == null) {
			return;
		}
		if (this.boroughId == null) {
			this.boroughId = new ArrayList<>();
		}
		this.boroughId.add(boroughId);

	}


	public static class Builder {

		protected Parent parent = new Parent();

		private Builder() {
		}


		public Builder withCountry(String country) {
			parent.addCountry(country);
			return this;
		}

		public Builder withPostalCode(String postalCode) {

			parent.addPostalCode(postalCode);
			return this;
		}

		public Builder withLocaladmin(String localadmin) {
			parent.addLocaladmin(localadmin);
			return this;
		}

		public Builder withLocality(String locality) {
			parent.addLocality(locality);
			return this;
		}

		public Builder withCounty(String county) {
			parent.addCounty(county);
			return this;
		}


		public Builder withBorough(String borough) {
			parent.addBorough(borough);
			return this;
		}

		public Builder withCountryId(String countryId) {
			parent.addCountryId(countryId);
			return this;
		}

		public Builder withPostalCodeId(String postalCodeId) {
			parent.addPostalCodeId(postalCodeId);
			return this;
		}

		public Builder withLocaladminId(String localadminId) {
			parent.addLocaladminId(localadminId);
			return this;
		}

		public Builder withLocalityId(String localityId) {
			parent.addLocalityId(localityId);
			return this;
		}

		public Builder withCountyId(String countyId) {
			parent.addCountyId(countyId);
			return this;
		}

		public Builder withBoroughId(String boroughId) {
			parent.addBoroughId(boroughId);
			return this;
		}

		public Parent build() {
			return parent;
		}
	}

}
