package no.rutebanken.marduk.rest;

import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

@Component
@Produces("application/json")
@Path("/appstatus")
public class ApplicationStatusResource {

    @GET
    @Path("/ready")
    public Response isReady() {
        //TODO
        //SFTP?
        //BlobStore?
        //ActiveMQ?
        //Chouette?

        return Response.ok().build();
    }

    @GET
    @Path("/up")
    public Response isUp() {
        return Response.ok().build();
    }

}
