package no.rutebanken.marduk.rest;

import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@Component
//@Produces("application/json")
@Path("/health")
public class ApplicationStatusResource {

    @GET
    @Path("/ready")
    public Response isReady() {
       return Response.ok().build();
    }

    @GET
    @Path("/live")
    public Response isLive() {
        return Response.ok().build();
    }

}
