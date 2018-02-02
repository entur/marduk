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

package no.rutebanken.marduk.geocoder.routes.pelias;

import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class PeliasUpdateStatusService {

	public enum Status {IDLE, BUILDING, ABORT}

	private Status status = Status.IDLE;
	private Date started = new Date();

	public Status getStatus() {
		return status;
	}

	public void setIdle() {
		status = Status.IDLE;
		started = new Date();
	}

	public void setBuilding() {
		status = Status.BUILDING;
		started = new Date();
	}

	public void signalAbort() {
		if (status == Status.BUILDING) {
			status = Status.ABORT;
			started = new Date();
		}
	}
}
