package no.rutebanken.marduk.validation;

import no.rutebanken.marduk.exceptions.MardukException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_TIMETABLE;
import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_TIMETABLE_FINLAND;
import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_TIMETABLE_SWEDEN;

/**
 * Which NeTEx validation profile antu should apply to a dataset.
 *
 * <p>Chosen from the codespace alone: two country lists, and Norway for everything else. The profile name
 * travels to antu as the {@code EnturValidationProfile} attribute and is pinned by {@code WireContractTest}.
 *
 * <p>Was {@code direct:setNetexValidationProfile}, reached from six places. The route's job was to pick a
 * string and put it in a header, which is a function.
 */
@Component
public class NetexValidationProfiles {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetexValidationProfiles.class);

    private final List<String> swedishCodespaces;
    private final List<String> finnishCodespaces;

    public NetexValidationProfiles(
            @Value("${antu.validation.sweden.codespaces:}") List<String> swedishCodespaces,
            @Value("${antu.validation.finland.codespaces:OYM}") List<String> finnishCodespaces) {
        this.swedishCodespaces = swedishCodespaces;
        this.finnishCodespaces = finnishCodespaces;
    }

    /**
     * @param datasetReferential the dataset referential, with or without the {@code rb_} prefix
     * @throws MardukException if it is absent - the Camel version threw a NullPointerException from inside a
     *                         predicate here, which failed the exchange just as loudly but said nothing useful
     */
    public String profileFor(String datasetReferential) {
        if (datasetReferential == null) {
            throw new MardukException("Cannot choose a validation profile without a dataset referential");
        }
        String codespace = datasetReferential.replace("rb_", "").toUpperCase(Locale.ROOT);
        if (swedishCodespaces.contains(codespace)) {
            LOGGER.info("Applying validation rules for Timetable data/Sweden");
            return VALIDATION_PROFILE_TIMETABLE_SWEDEN;
        }
        if (finnishCodespaces.contains(codespace)) {
            LOGGER.info("Applying validation rules for Timetable data/Finland");
            return VALIDATION_PROFILE_TIMETABLE_FINLAND;
        }
        LOGGER.info("Applying validation rules for Timetable data/Norway");
        return VALIDATION_PROFILE_TIMETABLE;
    }
}
