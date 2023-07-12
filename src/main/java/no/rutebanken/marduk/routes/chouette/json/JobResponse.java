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

package no.rutebanken.marduk.routes.chouette.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JobResponse {

	private Integer id;
	public Integer getId() {
		return id;
	}
	
	private String referential;
	private String action;
	private String type;
	private Long created;
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private Long started;
	private Long updated;
	private Status status;

	public Status getStatus() {
		return status;
	}

	public JobResponse setId(Integer id) {
		this.id = id;
		return this;
	}

	public String getReferential() {
		return referential;
	}

	public JobResponse setReferential(String referential) {
		this.referential = referential;
		return this;
	}

	public String getAction() {
		return action;
	}

	public JobResponse setAction(String action) {
		this.action = action;
		return this;
	}

	public String getType() {
		return type;
	}

	public JobResponse setType(String type) {
		this.type = type;
		return this;
	}

	public Long getCreated() {
		return created;
	}

	public JobResponse setCreated(Long created) {
		this.created = created;
		return this;
	}

	public Long getStarted() {
		return started;
	}

	public JobResponse setStarted(Long started) {
		this.started = started;
		return this;
	}

	public Long getUpdated() {
		return updated;
	}

	public JobResponse setUpdated(Long updated) {
		this.updated = updated;
		return this;
	}

	public JobResponse setStatus(Status status) {
		this.status = status;
		return this;
	}
}
