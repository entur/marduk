package no.rutebanken.marduk.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Provide a shared ObjectMapper with a common configuration that can be reused across the application.
 * ObjectMapper is thread-safe, but contains internal structures that are synchronized.
 * The best practice is not to use it directly but to create type-specific ObjectReaders and ObjectWriters.
 * ObjectReader and ObjectWriter are both thread-safe and do not require synchronization.
 * Example:
 * <pre>
 * private static final ObjectReader OBJECT_READER = ObjectMapperFactory.getSharedObjectMapper().readerFor(Provider.class);
 * OBJECT_READER.readValue(jsonString)
 * </pre>
 * However ObjectMapper.readTree() does not have an equivalent method in ObjectReader. In that case a copy of the ObjectMapper can be created to reduce contention:
 * <pre>
 * private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getSharedObjectMapper().copy();
 * OBJECT_MAPPER.readTree(...)
 * </pre>
 */
public final class ObjectMapperFactory {

    private static final ObjectMapper SHARED_OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private ObjectMapperFactory() {
    }

    public static ObjectMapper getSharedObjectMapper() {
        return SHARED_OBJECT_MAPPER;
    }
}
