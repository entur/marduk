package no.rutebanken.marduk.rest;

import no.rutebanken.marduk.repository.ProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

@Component
@Produces("application/json")
@Path("/appstatus")
public class ApplicationStatusResource {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    ProviderRepository providerRepository;

    @GET
    @Path("/ready")
    public Response isReady() {
        logger.debug("Checking readiness...");
        //TODO
        //SFTP?
        //BlobStore?
        //ActiveMQ?
        //Chouette?

        if ( providerRepository.isConnected() ) {
           return Response.ok().build();
        } else {
            return Response.serverError().build();
        }
    }

    @GET
    @Path("/up")
    public Response isUp() {
        return Response.ok().build();
    }

}
