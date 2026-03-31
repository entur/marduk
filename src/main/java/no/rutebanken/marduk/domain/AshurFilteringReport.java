package no.rutebanken.marduk.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AshurFilteringReport(
    LocalDateTime created,
    String correlationId,
    String codespace,
    String filterProfile,
    String status,
    String reason,
    Map<String, Long> entityTypeCounts
) {}
