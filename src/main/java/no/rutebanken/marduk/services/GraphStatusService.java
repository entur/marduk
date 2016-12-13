package no.rutebanken.marduk.services;

import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class GraphStatusService {

    public enum Status { IDLE, BUILDING }

    private Status status = Status.IDLE;
    private Date started = new Date();

    public GraphStatusResponse getStatus() {
        return new GraphStatusResponse(status.name(), started.getTime());
    }

    public void setIdle() {
        status = Status.IDLE;
        started = new Date();
    }

    public void setBuilding() {
        status = Status.BUILDING;
        started = new Date();
    }

}
