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

package no.rutebanken.marduk.geocoder.routes.pelias.kartverket;

import org.apache.commons.lang3.StringUtils;
import org.beanio.annotation.Field;
import org.beanio.annotation.Record;

@Record(minOccurs = 0, maxOccurs = -1)
public class KartverketAddress {
	@Field(at = 0)
	private String addresseId;
	@Field(at = 1)
	private String type;
	@Field(at = 2)
	private String kommunenr;

	@Field(at = 3)
	private String addressekode;
	@Field(at = 4)
	private String addressenavn;

	@Field(at = 5)
	private String kortAddressenavn;

	@Field(at = 6)
	private String nr;

	@Field(at = 7)
	private String bokstav;

	@Field(at = 8)
	private String gardsnr;

	@Field(at = 9)
	private String bruksnr;

	@Field(at = 10)
	private String festenr;

	@Field(at = 11)
	private String seksjonsnr;
	@Field(at = 12)
	private String undernr;

	@Field(at = 13)
	private String kortAddresseTilleggsnavn;

	@Field(at = 14)
	private String tilleggsnavnKildekode;

	@Field(at = 15)
	private String tilleggsnavnKildenavn;

	@Field(at = 16)
	private String koordinatsystemKode;

	@Field(at = 17)
	private Double nord;

	@Field(at = 18)
	private Double ost;

	@Field(at = 19)
	private String grunnkretsnr;

	@Field(at = 20)
	private String grunnkretsnavn;

	@Field(at = 21)
	private String kirkesognnr;

	@Field(at = 22)
	private String kirkesognnavn;

	@Field(at = 23)
	private String tettstednr;

	@Field(at = 24)
	private String tettstednavn;

	@Field(at = 25)
	private String valgkretsnr;

	@Field(at = 26)
	private String valgkretsnavn;

	@Field(at = 27)
	private String postnrn;

	@Field(at = 28)
	private String postnummerområde;


	protected String pad(String val, int length) {
		if (val == null) {
			return null;
		}
		return StringUtils.leftPad(val, length, "0");
	}

	public String getFullKommuneNo() {
		return pad(kommunenr, 4);
	}

	public String getFylkesNo() {
		if (kommunenr == null) {
			return null;
		}
		return getFullKommuneNo().substring(0, 2);
	}

	public String getFullGrunnkretsNo() {
		if (kommunenr == null || grunnkretsnr == null) {
			return null;
		}
		return getFullKommuneNo() + pad(grunnkretsnr, 4);
	}


	public String getAddresseId() {
		return addresseId;
	}

	public void setAddresseId(String addresseId) {
		this.addresseId = addresseId;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getKommunenr() {
		return kommunenr;
	}

	public void setKommunenr(String kommunenr) {
		this.kommunenr = kommunenr;
	}

	public String getAddressekode() {
		return addressekode;
	}

	public void setAddressekode(String addressekode) {
		this.addressekode = addressekode;
	}

	public String getAddressenavn() {
		return addressenavn;
	}

	public void setAddressenavn(String addressenavn) {
		this.addressenavn = addressenavn;
	}

	public String getKortAddressenavn() {
		return kortAddressenavn;
	}

	public void setKortAddressenavn(String kortAddressenavn) {
		this.kortAddressenavn = kortAddressenavn;
	}

	public String getNr() {
		return nr;
	}

	public void setNr(String nr) {
		this.nr = nr;
	}

	public String getBokstav() {
		return bokstav;
	}

	public void setBokstav(String bokstav) {
		this.bokstav = bokstav;
	}

	public String getGardsnr() {
		return gardsnr;
	}

	public void setGardsnr(String gardsnr) {
		this.gardsnr = gardsnr;
	}

	public String getBruksnr() {
		return bruksnr;
	}

	public void setBruksnr(String bruksnr) {
		this.bruksnr = bruksnr;
	}

	public String getFestenr() {
		return festenr;
	}

	public void setFestenr(String festenr) {
		this.festenr = festenr;
	}

	public String getSeksjonsnr() {
		return seksjonsnr;
	}

	public void setSeksjonsnr(String seksjonsnr) {
		this.seksjonsnr = seksjonsnr;
	}

	public String getUndernr() {
		return undernr;
	}

	public void setUndernr(String undernr) {
		this.undernr = undernr;
	}

	public String getKortAddresseTilleggsnavn() {
		return kortAddresseTilleggsnavn;
	}

	public void setKortAddresseTilleggsnavn(String kortAddresseTilleggsnavn) {
		this.kortAddresseTilleggsnavn = kortAddresseTilleggsnavn;
	}

	public String getTilleggsnavnKildekode() {
		return tilleggsnavnKildekode;
	}

	public void setTilleggsnavnKildekode(String tilleggsnavnKildekode) {
		this.tilleggsnavnKildekode = tilleggsnavnKildekode;
	}

	public String getTilleggsnavnKildenavn() {
		return tilleggsnavnKildenavn;
	}

	public void setTilleggsnavnKildenavn(String tilleggsnavnKildenavn) {
		this.tilleggsnavnKildenavn = tilleggsnavnKildenavn;
	}

	public String getKoordinatsystemKode() {
		return koordinatsystemKode;
	}

	public void setKoordinatsystemKode(String koordinatsystemKode) {
		this.koordinatsystemKode = koordinatsystemKode;
	}


	public Double getNord() {
		return nord;
	}

	public void setNord(Double nord) {
		this.nord = nord;
	}

	public Double getOst() {
		return ost;
	}

	public void setOst(Double ost) {
		this.ost = ost;
	}

	public String getGrunnkretsnr() {
		return grunnkretsnr;
	}

	public void setGrunnkretsnr(String grunnkretsnr) {
		this.grunnkretsnr = grunnkretsnr;
	}

	public String getGrunnkretsnavn() {
		return grunnkretsnavn;
	}

	public void setGrunnkretsnavn(String grunnkretsnavn) {
		this.grunnkretsnavn = grunnkretsnavn;
	}

	public String getKirkesognnr() {
		return kirkesognnr;
	}

	public void setKirkesognnr(String kirkesognnr) {
		this.kirkesognnr = kirkesognnr;
	}

	public String getKirkesognnavn() {
		return kirkesognnavn;
	}

	public void setKirkesognnavn(String kirkesognnavn) {
		this.kirkesognnavn = kirkesognnavn;
	}

	public String getTettstednr() {
		return tettstednr;
	}

	public void setTettstednr(String tettstednr) {
		this.tettstednr = tettstednr;
	}

	public String getTettstednavn() {
		return tettstednavn;
	}

	public void setTettstednavn(String tettstednavn) {
		this.tettstednavn = tettstednavn;
	}

	public String getValgkretsnr() {
		return valgkretsnr;
	}

	public void setValgkretsnr(String valgkretsnr) {
		this.valgkretsnr = valgkretsnr;
	}

	public String getValgkretsnavn() {
		return valgkretsnavn;
	}

	public void setValgkretsnavn(String valgkretsnavn) {
		this.valgkretsnavn = valgkretsnavn;
	}

	public String getPostnrn() {
		return postnrn;
	}

	public void setPostnrn(String postnrn) {
		this.postnrn = postnrn;
	}

	public String getPostnummerområde() {
		return postnummerområde;
	}

	public void setPostnummerområde(String postnummerområde) {
		this.postnummerområde = postnummerområde;
	}
}
