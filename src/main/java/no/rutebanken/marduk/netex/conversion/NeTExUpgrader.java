/*
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
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
 */

package no.rutebanken.marduk.netex.conversion;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;

/**
 * Converts a NeTEx document from an older schema version to a newer one. See {@link NeTExConverter} for the
 * threading and memory characteristics, and {@link NeTExDowngrader} for the opposite direction.
 */
public class NeTExUpgrader extends NeTExConverter {

    /**
     * Supported conversions.
     */
    public enum Conversion implements NeTExConversion {
        /**
         * NeTEx 1.15 to NeTEx 1.16. Applies the changes made to {@code DatedServiceJourney} in 1.16:
         * <ul>
         *     <li>the additional {@code DatedServiceJourneyRef} elements referencing the replaced journeys are moved into a
         *     {@code replacedJourneys} container as {@code DatedVehicleJourneyRef} elements, placed after the remaining
         *     journey reference;</li>
         *     <li>the version prefix of {@code PublicationDelivery/@version} is rewritten from {@code 1.15} to {@code 1.16}.</li>
         * </ul>
         * {@code DatedServiceJourneyRef} elements used outside a {@code DatedServiceJourney} are not handled: they have no
         * equivalent in 1.16.
         */
        V1_15_TO_V1_16("netex/conversion/netex-1.15-to-1.16.xsl", NetexVersion.v1_15, NetexVersion.v1_16);

        private final String stylesheet;
        private final NetexVersion sourceVersion;
        private final NetexVersion targetVersion;

        Conversion(String stylesheet, NetexVersion sourceVersion, NetexVersion targetVersion) {
            this.stylesheet = stylesheet;
            this.sourceVersion = sourceVersion;
            this.targetVersion = targetVersion;
        }

        @Override
        public String getStylesheet() {
            return stylesheet;
        }

        @Override
        public NetexVersion getSourceVersion() {
            return sourceVersion;
        }

        @Override
        public NetexVersion getTargetVersion() {
            return targetVersion;
        }
    }

    /**
     * Conversion from the previous NeTEx version to the latest one supported by the conversions of this package.
     */
    public static final Conversion LATEST = Conversion.V1_15_TO_V1_16;

    /**
     * Return a shared, thread-safe upgrader for the given conversion, compiling the stylesheet on first use.
     *
     * @param conversion the conversion to perform, {@link #LATEST} if null.
     */
    public static NeTExUpgrader getNeTExUpgrader(Conversion conversion) throws IOException, TransformerConfigurationException {
        Conversion effectiveConversion = conversion == null ? LATEST : conversion;
        return getConverter(effectiveConversion, NeTExUpgrader.class, () -> new NeTExUpgrader(effectiveConversion));
    }

    /**
     * Return a shared, thread-safe upgrader for the {@link #LATEST} conversion.
     */
    public static NeTExUpgrader getNeTExUpgrader() throws IOException, TransformerConfigurationException {
        return getNeTExUpgrader(null);
    }

    /**
     * Use static getNeTExUpgrader to avoid compiling more stylesheets than needed.
     */
    public NeTExUpgrader() throws IOException, TransformerConfigurationException {
        this(LATEST);
    }

    /**
     * Use static getNeTExUpgrader to avoid compiling more stylesheets than needed.
     */
    public NeTExUpgrader(Conversion conversion) throws IOException, TransformerConfigurationException {
        super(conversion);
    }

    @Override
    public Conversion getConversion() {
        return (Conversion) super.getConversion();
    }

    /**
     * Transform a document in the source version of this upgrader's {@link Conversion} into the target version.
     * Same as {@link #convert(Source, Result)}.
     */
    public void upgrade(Source source, Result result) throws TransformerException {
        convert(source, result);
    }

}
