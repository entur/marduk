package no.rutebanken.marduk.services;

public class GraphStatusResponse {

    public String status;

    public Long started;

    GraphStatusResponse(String status, Long started){
        this.status = status;
        this.started = started;
    }
}