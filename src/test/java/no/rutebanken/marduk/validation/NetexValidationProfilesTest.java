package no.rutebanken.marduk.validation;

import no.rutebanken.marduk.exceptions.MardukException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetexValidationProfilesTest {

    private final NetexValidationProfiles profiles =
            new NetexValidationProfiles(List.of("VAN", "VAS"), List.of("OYM"));

    @Test
    void aSwedishCodespaceGetsTheSwedishProfile() {
        assertEquals("TimetableSweden", profiles.profileFor("VAN"));
    }

    @Test
    void aFinnishCodespaceGetsTheFinnishProfile() {
        assertEquals("TimetableFinland", profiles.profileFor("OYM"));
    }

    @Test
    void everythingElseGetsTheNorwegianProfile() {
        assertEquals("Timetable", profiles.profileFor("RUT"));
    }

    @Test
    void theRbPrefixIsStrippedBeforeMatching() {
        // The referential arrives as rb_van once the data has been transferred to the central dataspace,
        // and it is the same country either way.
        assertEquals("TimetableSweden", profiles.profileFor("rb_VAN"));
        assertEquals("TimetableFinland", profiles.profileFor("rb_OYM"));
    }

    @Test
    void matchingIsCaseInsensitiveOnTheCodespace() {
        assertEquals("TimetableSweden", profiles.profileFor("rb_van"));
    }

    @Test
    void anEmptyCountryListMatchesNothing() {
        // The deployed default for Sweden is empty, so every codespace has to fall through to Norway.
        assertEquals("Timetable", new NetexValidationProfiles(List.of(), List.of("OYM")).profileFor("VAN"));
    }

    @Test
    void aMissingReferentialFailsWithSomethingReadable() {
        // The Camel version threw a NullPointerException from inside a predicate here, which failed the
        // exchange just as loudly but said nothing about what was missing.
        MardukException thrown = assertThrows(MardukException.class, () -> profiles.profileFor(null));
        assertEquals("Cannot choose a validation profile without a dataset referential", thrown.getMessage());
    }
}
