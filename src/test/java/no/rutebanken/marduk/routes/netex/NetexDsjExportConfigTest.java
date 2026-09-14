/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.routes.netex;

import org.junit.jupiter.api.Test;

import static no.rutebanken.marduk.routes.netex.NetexDsjExportConfig.parseVariant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetexDsjExportConfigTest {

    private static final String PROPERTY = "netex.export.dsj.api.default.variant";

    @Test
    void configuredVariantsAreParsedCaseInsensitivelyAndTrimmed() {
        assertThat(parseVariant("legacy", PROPERTY)).isEqualTo(NetexDsjExportConfig.Variant.LEGACY);
        assertThat(parseVariant("new", PROPERTY)).isEqualTo(NetexDsjExportConfig.Variant.NEW);
        assertThat(parseVariant("LEGACY", PROPERTY)).isEqualTo(NetexDsjExportConfig.Variant.LEGACY);
        assertThat(parseVariant("  New  ", PROPERTY)).isEqualTo(NetexDsjExportConfig.Variant.NEW);
    }

    /**
     * The value is resolved at startup rather than per request, so a typo must fail loudly and say which property is
     * at fault instead of turning every dataset download into a 400.
     */
    @Test
    void anUnparseableVariantNamesThePropertyAtFault() {
        assertThatThrownBy(() -> parseVariant("legacyy", PROPERTY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PROPERTY)
                .hasMessageContaining("legacyy")
                .hasMessageContaining("expected legacy or new");
    }

    @Test
    void anEmptyOrMissingVariantIsRejectedTheSameWay() {
        assertThatThrownBy(() -> parseVariant("", PROPERTY)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> parseVariant("   ", PROPERTY)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> parseVariant(null, PROPERTY)).isInstanceOf(IllegalStateException.class);
    }

    /**
     * The resolution runs on the bean itself, so both properties are checked before the context is up.
     */
    @Test
    void resolvingBothVariantsReportsTheOffendingPropertyOfEach() {
        assertThatThrownBy(() -> parseVariant("bogus", "netex.export.dsj.default.variant"))
                .hasMessageContaining("netex.export.dsj.default.variant");
        assertThatThrownBy(() -> parseVariant("bogus", "netex.export.dsj.api.default.variant"))
                .hasMessageContaining("netex.export.dsj.api.default.variant");
    }
}
