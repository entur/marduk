package no.rutebanken.marduk.routes.admin;

import no.rutebanken.marduk.services.IdempotentFileStoreService;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ApplicationAdminRoute extends RouteBuilder {

    @Autowired
    IdempotentFileStoreService idempotentFileStoreService;

    @Override
    public void configure() throws Exception {
        from("direct:cleanIdempotentFileStore")
                .bean(idempotentFileStoreService, "clean");
    }
}
