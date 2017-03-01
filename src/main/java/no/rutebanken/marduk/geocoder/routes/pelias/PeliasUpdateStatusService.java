package no.rutebanken.marduk.geocoder.routes.pelias;

import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class PeliasUpdateStatusService {

	public enum Status { IDLE, BUILDING, ABORT }

	private Status status =Status.IDLE;
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

	public void signalAbort(){
		status = Status.ABORT;
		started = new Date();
	}
}
