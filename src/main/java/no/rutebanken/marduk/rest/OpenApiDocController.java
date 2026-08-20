package no.rutebanken.marduk.rest;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The API document: its title, and the second path it is reachable at.
 *
 * <p>springdoc generates it from the controller annotations and serves it at
 * {@code springdoc.api-docs.path}, set to {@code /services/openapi} so the {@code .yaml} spelling lands where
 * the REST DSL used to publish it. The REST DSL also declared
 * {@code /services/timetable_admin/openapi.yaml} as a second endpoint proxying the same document; that is the
 * forward below.
 */
@OpenAPIDefinition(info = @Info(title = "Timetable Admin API", version = "1.0"))
@Controller
public class OpenApiDocController {

    @GetMapping("/services/timetable_admin/openapi.yaml")
    public String adminApiDocument() {
        return "forward:/services/openapi.yaml";
    }
}
