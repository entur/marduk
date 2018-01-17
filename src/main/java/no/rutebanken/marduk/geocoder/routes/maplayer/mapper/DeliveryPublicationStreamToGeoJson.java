package no.rutebanken.marduk.geocoder.routes.maplayer.mapper;

import no.rutebanken.marduk.exceptions.FileValidationException;
import no.rutebanken.marduk.netex.PublicationDeliveryHelper;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.apache.commons.io.IOUtils;
import org.rutebanken.netex.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static javax.xml.bind.JAXBContext.newInstance;

@Service
public class DeliveryPublicationStreamToGeoJson {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryPublicationStreamToGeoJson.class);

    public DeliveryPublicationStreamToGeoJson() {

    }

    public Collection<String> transform(InputStream publicationDeliveryStream) {
        logger.info("Transform {}", publicationDeliveryStream);


        try {
            logger.info("{}", IOUtils.toString(publicationDeliveryStream));
            PublicationDeliveryStructure deliveryStructure = PublicationDeliveryHelper.unmarshall(publicationDeliveryStream);
            fromDeliveryPublicationStructure(deliveryStructure);
        } catch (Exception e) {
            throw new FileValidationException("Parsing of DeliveryPublications failed: " + e.getMessage(), e);
        }
        return new ArrayList<>();
    }

    private void fromDeliveryPublicationStructure(PublicationDeliveryStructure publicationDeliveryStructure) {
        List<StopPlace> stops = PublicationDeliveryHelper.resolveStops(publicationDeliveryStructure)
                .peek(stop -> logger.info("{}", stop))
                .collect(Collectors.toList());
    }


}
