package no.rutebanken.marduk.geocoder.sosi;

import com.google.common.collect.Sets;

import java.util.Set;

public class SosiSpellingStatusCode {

    public static final String ACCEPTED = "vedtatt";

    public static final String INTERNATIONAL = "internasjonal";

    public static final String APPROVED = "godkjent";

    public static final String PRIVATE = "privat";

    private static Set<String> ACTIVE_CODES = Sets.newHashSet(ACCEPTED, INTERNATIONAL, APPROVED, PRIVATE);

    public static boolean isActive(String code) {
        return ACTIVE_CODES.contains(code);
    }

}

