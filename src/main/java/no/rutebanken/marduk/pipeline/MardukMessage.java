package no.rutebanken.marduk.pipeline;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A unit of work moving through the pipeline: a header map, a body and a set of properties.
 *
 * <p>Replaces Camel's {@code Exchange}. The accessors are deliberately named after the ones they
 * replace, so converting a route is a mechanical edit that a reviewer can check against the original.
 *
 * <p>Three of Camel's behaviours are reproduced here rather than left to the caller, because each one is
 * load-bearing somewhere in marduk and invisible at the call site:
 *
 * <ul>
 *   <li><b>Headers are case-insensitive</b>, keeping whichever spelling was set first.
 *       {@code removeHttpHeaders} strips {@code "Authorization"} by that literal name, and an HTTP/2
 *       client sends {@code authorization}; on a case-sensitive map the token would survive into the
 *       outbound call to Chouette. The original spelling is kept because PubSub attribute names are
 *       case-sensitive on the wire.
 *   <li><b>Values are coerced on read.</b> A header that arrives as a PubSub attribute is always a
 *       {@code String}, while the same header set in-process is often a {@code Long} or an enum. Both
 *       paths work today only because Camel converted, so {@link #getHeader(String, Class)} does too.
 *   <li><b>Converting a stream body caches the result.</b> Reading an {@code InputStream} consumes it,
 *       which is what Camel's per-route stream caching hid. Converting to {@code byte[]} or
 *       {@code String} stores the bytes back, so a second read sees the same content.
 * </ul>
 *
 * <p>Not thread-safe. One message is owned by one thread at a time; batches hold separate instances.
 */
public final class MardukMessage {

    private final Headers headers = new Headers();
    private final Map<String, Object> properties = new HashMap<>();
    private Object body;

    public MardukMessage() {
        // an empty message, the usual starting point for a scheduled job
    }

    public MardukMessage(Map<String, ?> headers, Object body) {
        headers.forEach(this.headers::put);
        this.body = body;
    }

    /** A message with the same headers, properties and body. Mutating one does not affect the other. */
    public MardukMessage copy() {
        MardukMessage copy = new MardukMessage();
        copy.headers.putAll(this.headers);
        copy.properties.putAll(this.properties);
        copy.body = this.body;
        return copy;
    }

    // ---------------------------------------------------------------- headers

    public Object getHeader(String name) {
        return headers.get(name);
    }

    public <T> T getHeader(String name, Class<T> type) {
        return convert(headers.get(name), type);
    }

    public <T> T getHeader(String name, T defaultValue, Class<T> type) {
        T value = getHeader(name, type);
        return value != null ? value : defaultValue;
    }

    public MardukMessage setHeader(String name, Object value) {
        headers.put(name, value);
        return this;
    }

    /** Sets the header only when {@code value} is non-null, mirroring {@code setHeader} on an absent source. */
    public MardukMessage setHeaderIfPresent(String name, Object value) {
        if (value != null) {
            headers.put(name, value);
        }
        return this;
    }

    public MardukMessage removeHeader(String name) {
        headers.remove(name);
        return this;
    }

    /**
     * Removes every header whose name starts with {@code prefix}, case-insensitively. Replaces Camel's
     * wildcard {@code removeHeaders("CamelHttp*")} form; pass the prefix without the trailing star.
     */
    public MardukMessage removeHeadersStartingWith(String prefix) {
        headers.removeIf(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)));
        return this;
    }

    public MardukMessage removeAllHeaders() {
        headers.clear();
        return this;
    }

    /** An unmodifiable view in insertion order, with the original key spelling. */
    public Map<String, Object> getHeaders() {
        return headers.view();
    }

    public boolean hasHeader(String name) {
        return headers.get(name) != null;
    }

    /** Replaces the header map wholesale, as {@code Message.setHeaders} did. */
    public MardukMessage setHeaders(Map<String, ?> replacement) {
        headers.clear();
        replacement.forEach(headers::put);
        return this;
    }

    // ------------------------------------------------------------- properties

    /**
     * Values that must not travel: unlike a header, a property is never published as a PubSub attribute.
     * The local working directory the flexible-lines merge unpacks into is the one use.
     */
    public <T> T getProperty(String name, Class<T> type) {
        return convert(properties.get(name), type);
    }

    public MardukMessage setProperty(String name, Object value) {
        properties.put(name, value);
        return this;
    }

    // ------------------------------------------------------------------- body

    public Object getBody() {
        return body;
    }

    /**
     * The body as {@code type}. Converting a stream to bytes or text stores the bytes back onto the
     * message, so the body stays readable afterwards.
     */
    public <T> T getBody(Class<T> type) {
        if (body instanceof InputStream stream && (type == byte[].class || type == String.class)) {
            body = readFully(stream);
        }
        return convert(body, type);
    }

    public MardukMessage setBody(Object body) {
        this.body = body;
        return this;
    }

    // -------------------------------------------------------------- coercion

    @SuppressWarnings("unchecked")
    private static <T> T convert(Object value, Class<T> type) {
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        if (type == String.class) {
            return (T) asString(value);
        }
        if (type == Long.class) {
            return (T) (value instanceof Number number ? Long.valueOf(number.longValue()) : Long.valueOf(asString(value).trim()));
        }
        if (type == Integer.class) {
            return (T) (value instanceof Number number ? Integer.valueOf(number.intValue()) : Integer.valueOf(asString(value).trim()));
        }
        if (type == Boolean.class) {
            return (T) (value instanceof Boolean flag ? flag : Boolean.valueOf(asString(value).trim()));
        }
        if (type == byte[].class) {
            return (T) asBytes(value);
        }
        if (type == InputStream.class) {
            return (T) new ByteArrayInputStream(asBytes(value));
        }
        if (type.isEnum()) {
            return (T) Enum.valueOf((Class<Enum>) type.asSubclass(Enum.class), asString(value));
        }
        throw new IllegalArgumentException(
                "Cannot convert " + value.getClass().getName() + " to " + type.getName());
    }

    private static String asString(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (value instanceof InputStream stream) {
            return new String(readFully(stream), StandardCharsets.UTF_8);
        }
        return Objects.toString(value);
    }

    private static byte[] asBytes(Object value) {
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        if (value instanceof InputStream stream) {
            return readFully(stream);
        }
        return asString(value).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] readFully(InputStream stream) {
        try (InputStream toRead = stream) {
            return toRead.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the message body", e);
        }
    }

    /**
     * Insertion-ordered header map with case-insensitive lookup that keeps the spelling of whichever key
     * was inserted first, matching Camel. Re-setting a header under a different case updates the existing
     * entry rather than adding a second one, so a header cannot reach the wire twice under two spellings.
     */
    private static final class Headers {

        private final Map<String, Object> entries = new LinkedHashMap<>();
        private final Map<String, String> canonical = new HashMap<>();

        void put(String name, Object value) {
            String existing = canonical.putIfAbsent(key(name), name);
            entries.put(existing != null ? existing : name, value);
        }

        Object get(String name) {
            String actual = canonical.get(key(name));
            return actual == null ? null : entries.get(actual);
        }

        void remove(String name) {
            String actual = canonical.remove(key(name));
            if (actual != null) {
                entries.remove(actual);
            }
        }

        void removeIf(java.util.function.Predicate<String> nameMatches) {
            entries.keySet().removeIf(name -> {
                if (nameMatches.test(name)) {
                    canonical.remove(key(name));
                    return true;
                }
                return false;
            });
        }

        void putAll(Headers other) {
            other.entries.forEach(this::put);
        }

        void clear() {
            entries.clear();
            canonical.clear();
        }

        Map<String, Object> view() {
            return Collections.unmodifiableMap(entries);
        }

        private static String key(String name) {
            return name.toLowerCase(Locale.ROOT);
        }
    }
}
