package no.rutebanken.marduk.routes.mapbox.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.exceptions.FileValidationException;
import no.rutebanken.marduk.netex.PublicationDeliveryHelper;
import org.apache.camel.Exchange;
import org.geojson.Feature;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.StopPlace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class DeliveryPublicationStreamToGeoJson {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryPublicationStreamToGeoJson.class);

    private static final String STOP_PLACE_LOCAL_NAME = "StopPlace";

    private final StopPlaceToGeoJsonFeatureMapper stopPlaceToGeoJsonFeatureMapper;

    @Autowired
    public DeliveryPublicationStreamToGeoJson(StopPlaceToGeoJsonFeatureMapper stopPlaceToGeoJsonFeatureMapper) {
        this.stopPlaceToGeoJsonFeatureMapper = stopPlaceToGeoJsonFeatureMapper;
    }


    public OutputStream transform(InputStream publicationDeliveryStream) {
        AtomicInteger stops = new AtomicInteger();
        ObjectMapper jacksonObjectMapper = new ObjectMapper();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);

        boolean firstWritten = false;
        try {
            writeFeatureCollectionStart(outputStreamWriter);
            Unmarshaller unmarshaller = PublicationDeliveryHelper.createUnmarshaller();

            XMLEventReader xmlEventReader = XMLInputFactory.newInstance().createXMLEventReader(publicationDeliveryStream);

            while (xmlEventReader.hasNext()) {
                XMLEvent xmlEvent = xmlEventReader.peek();

                if (xmlEvent.isStartElement()) {
                    StartElement startElement = xmlEvent.asStartElement();
                    String localPartOfName = startElement.getName().getLocalPart();

                    if (localPartOfName.equals(STOP_PLACE_LOCAL_NAME)) {
                        if(firstWritten) {
                            outputStreamWriter.write(",\n");
                            outputStreamWriter.flush();
                        } else {
                            firstWritten = true;
                        }
                        handleStopPlace(unmarshaller, xmlEventReader, jacksonObjectMapper, outputStream, stops);
                    }
                }
                xmlEventReader.next();
            }
            writeFeatureCollectionEnd(outputStreamWriter);

        } catch (Exception e) {
            throw new FileValidationException("Parsing of DeliveryPublications failed: " + e.getMessage(), e);
        }
        return outputStream;
    }

    private void writeFeatureCollectionStart(OutputStreamWriter outputStreamWriter) throws IOException {
        outputStreamWriter.write("{\n\"features\": [");
        outputStreamWriter.flush();
    }

    private void writeFeatureCollectionEnd(OutputStreamWriter outputStreamWriter) throws IOException {
        outputStreamWriter.write("\n], \"type\": \"FeatureCollection\"\n}");
        outputStreamWriter.flush();
    }

    private void handleStopPlace(Unmarshaller unmarshaller, XMLEventReader xmlEventReader, ObjectMapper jacksonObjectMapper, OutputStream outputStream, AtomicInteger stops) throws JAXBException, IOException {
        StopPlace stopPlace = unmarshaller.unmarshal(xmlEventReader, StopPlace.class).getValue();

        Feature feature = stopPlaceToGeoJsonFeatureMapper.mapStopPlaceToGeoJson(stopPlace);

        jacksonObjectMapper.writeValue(outputStream, feature);

        stops.incrementAndGet();

        log(stops, StopPlace.class.getSimpleName());
    }

    private void log(AtomicInteger counter, String type) {
        if (counter.get() % 200 == 0) {
            logger.info("Transformed {} {}", counter.get(), type);
        }
    }
}
