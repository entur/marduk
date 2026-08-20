package no.rutebanken.marduk.repository;

/**
 * A store of keys already seen, used to reject a timetable file marduk has already imported.
 *
 * <p>Replaces {@code org.apache.camel.spi.IdempotentRepository}. The method contract is the same one
 * marduk's callers already rely on, so the duplicate-file behaviour is unchanged.
 */
public interface IdempotentRepository {

    /**
     * Adds the key if it is not already present.
     *
     * @return true if the key was added by this call, false if it was already there
     */
    boolean add(String key);

    boolean contains(String key);

    /**
     * Removes the key.
     *
     * @return true if a row was deleted
     */
    boolean remove(String key);

    void clear();
}
