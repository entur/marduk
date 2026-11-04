package no.rutebanken.marduk.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.pipeline.MardukMdc;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Establishes the log MDC for every HTTP request and clears it again afterwards.
 *
 * <p>Camel did this from {@code interceptFrom(".*")} and an {@code onCompletion}, so a request could not be
 * served without it. Without the filter each handler has to remember, and a Tomcat thread that served a
 * request which forgot logs the previous request's correlation id.
 *
 * <p>Ordered ahead of the security filter chain so an authentication or authorization failure is logged
 * with the caller's correlation id too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MardukMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        MardukMdc.clear();
        MardukMdc.setCorrelationId(request.getHeader(Constants.CORRELATION_ID));
        MardukMdc.setCodespaceIfMissing(request.getHeader(Constants.CHOUETTE_REFERENTIAL));
        try {
            chain.doFilter(request, response);
        } finally {
            MardukMdc.clear();
        }
    }
}
