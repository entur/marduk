package no.rutebanken.marduk.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ObjectMapperFactory {

    private static final ObjectMapper SHARED_OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    public static ObjectMapper getSharedObjectMapper() {
        return SHARED_OBJECT_MAPPER;
    }
}
