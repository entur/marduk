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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.XMLConstants;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Converts a NeTEx document from one schema version to another by applying an XSLT stylesheet bundled with
 * marduk. {@link NeTExDowngrader} and {@link NeTExUpgrader} provide the supported conversions.
 * <p>
 * The stylesheet is compiled once per {@link NeTExConversion} and reused; a new {@link Transformer} is created
 * for every call to {@link #convert(Source, Result)}, so an instance can be shared between threads.
 * <p>
 * The transformation is run by the JAXP {@link TransformerFactory} available at runtime (the JDK built-in
 * XSLT 1.0 processor unless another one is on the classpath). The JDK processor builds the whole document
 * in memory, so converting large exports requires a correspondingly large heap.
 * <p>
 * Only the schema differences listed on each conversion are handled: the output is guaranteed to be valid
 * against the target schema only if the input does not use other constructs that differ between the two
 * versions.
 */
public abstract class NeTExConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(NeTExConverter.class);

    /**
     * Creates a converter for a conversion whose stylesheet is not compiled yet.
     */
    @FunctionalInterface
    protected interface ConverterFactory<T extends NeTExConverter> {
        T create() throws IOException, TransformerConfigurationException;
    }

    private static final Map<NeTExConversion, NeTExConverter> CONVERTERS_PER_CONVERSION = new HashMap<>();

    /**
     * Return the shared converter for the given conversion, creating it with the factory on first use.
     * Used by the static accessors of the subclasses so that each stylesheet is compiled only once.
     */
    protected static synchronized <T extends NeTExConverter> T getConverter(NeTExConversion conversion, Class<T> type, ConverterFactory<T> factory)
            throws IOException, TransformerConfigurationException {
        NeTExConverter converter = CONVERTERS_PER_CONVERSION.get(conversion);
        if (converter == null) {
            converter = factory.create();
            CONVERTERS_PER_CONVERSION.put(conversion, converter);
        }
        return type.cast(converter);
    }

    private final NeTExConversion conversion;
    private final Templates templates;

    /**
     * Compiles the stylesheet of the conversion. Subclasses expose static accessors returning a shared instance,
     * which should be preferred to avoid compiling more stylesheets than needed.
     */
    protected NeTExConverter(NeTExConversion conversion) throws IOException, TransformerConfigurationException {
        this.conversion = conversion;
        String resourceName = conversion.getStylesheet();
        LOGGER.info("Loading resource: {}", resourceName);
        URL resource = getClass().getClassLoader().getResource(resourceName);
        if (resource == null) {
            throw new IOException("Cannot load resource " + resourceName);
        }
        TransformerFactory factory = createTransformerFactory();
        try (InputStream inputStream = resource.openStream()) {
            templates = factory.newTemplates(new StreamSource(inputStream, resource.toExternalForm()));
        }
    }

    private static TransformerFactory createTransformerFactory() throws TransformerConfigurationException {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        setAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        return factory;
    }

    private static void setAttributeIfSupported(TransformerFactory factory, String name, Object value) {
        try {
            factory.setAttribute(name, value);
        } catch (IllegalArgumentException e) {
            LOGGER.debug("Attribute {} not supported by {}", name, factory.getClass().getName());
        }
    }

    public NeTExConversion getConversion() {
        return conversion;
    }

    /**
     * @return the compiled stylesheet, for callers that need to configure the {@link Transformer} themselves.
     */
    public Templates getTemplates() {
        return templates;
    }

    /**
     * Transform a document in the source version of this converter's {@link NeTExConversion} into the target version.
     *
     * @param source the input document, for instance a {@link StreamSource} over a file.
     * @param result where the converted document is written, for instance a {@link javax.xml.transform.stream.StreamResult}.
     */
    public void convert(Source source, Result result) throws TransformerException {
        Transformer transformer = templates.newTransformer();
        transformer.transform(source, result);
    }

}
