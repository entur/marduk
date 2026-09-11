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
 * Converts a NeTEx document from a newer schema version to an older one. See {@link NeTExConverter} for the
 * threading and memory characteristics, and {@link NeTExUpgrader} for the opposite direction.
 */
public class NeTExDowngrader extends NeTExConverter {

    /**
     * Supported conversions.
     */
    public enum Conversion implements NeTExConversion {
        /**
         * NeTEx 1.16 to NeTEx 1.15. Reverts the changes made to {@code DatedServiceJourney} in 1.16:
         * <ul>
         *     <li>{@code replacedJourneys/DatedVehicleJourneyRef} and {@code replacedJourneys/NormalDatedVehicleJourneyRef}
         *     become additional {@code DatedServiceJourneyRef} elements following the journey reference;</li>
         *     <li>{@code UicOperatingPeriod} is dropped when an {@code OperatingDayRef} is present (the two were mutually
         *     exclusive in 1.15);</li>
         *     <li>the version prefix of {@code PublicationDelivery/@version} is rewritten from {@code 1.16} to {@code 1.15}.</li>
         * </ul>
         */
        V1_16_TO_V1_15("netex/conversion/netex-1.16-to-1.15.xsl", NetexVersion.v1_16, NetexVersion.v1_15);

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
     * Conversion from the latest NeTEx version supported by the conversions of this package to the previous one.
     */
    public static final Conversion LATEST = Conversion.V1_16_TO_V1_15;

    /**
     * Return a shared, thread-safe downgrader for the given conversion, compiling the stylesheet on first use.
     *
     * @param conversion the conversion to perform, {@link #LATEST} if null.
     */
    public static NeTExDowngrader getNeTExDowngrader(Conversion conversion) throws IOException, TransformerConfigurationException {
        Conversion effectiveConversion = conversion == null ? LATEST : conversion;
        return getConverter(effectiveConversion, NeTExDowngrader.class, () -> new NeTExDowngrader(effectiveConversion));
    }

    /**
     * Return a shared, thread-safe downgrader for the {@link #LATEST} conversion.
     */
    public static NeTExDowngrader getNeTExDowngrader() throws IOException, TransformerConfigurationException {
        return getNeTExDowngrader(null);
    }

    /**
     * Use static getNeTExDowngrader to avoid compiling more stylesheets than needed.
     */
    public NeTExDowngrader() throws IOException, TransformerConfigurationException {
        this(LATEST);
    }

    /**
     * Use static getNeTExDowngrader to avoid compiling more stylesheets than needed.
     */
    public NeTExDowngrader(Conversion conversion) throws IOException, TransformerConfigurationException {
        super(conversion);
    }

    @Override
    public Conversion getConversion() {
        return (Conversion) super.getConversion();
    }

    /**
     * Transform a document in the source version of this downgrader's {@link Conversion} into the target version.
     * Same as {@link #convert(Source, Result)}.
     */
    public void downgrade(Source source, Result result) throws TransformerException {
        convert(source, result);
    }

}
