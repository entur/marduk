package no.rutebanken.marduk.netex;

import org.rutebanken.netex.model.*;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

import static javax.xml.bind.JAXBContext.newInstance;

/**
 * Common useful methods for resolving parts of NeTEx
 */
public class PublicationDeliveryHelper {

    public static Stream<StopPlace> resolveStops(PublicationDeliveryStructure publicationDelivery) {

        return resolveSiteFrames(publicationDelivery)
                .filter(siteFrame -> siteFrame.getStopPlaces() != null)
                .map(Site_VersionFrameStructure::getStopPlaces)
                .filter(Objects::nonNull)
                .map(StopPlacesInFrame_RelStructure::getStopPlace)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream);
    }

    public static Stream<SiteFrame> resolveSiteFrames(PublicationDeliveryStructure publicationDeliveryStructure) {
        return publicationDeliveryStructure.getDataObjects().getCompositeFrameOrCommonFrame().stream()
                .map(JAXBElement::getValue)
                .filter(commonVersionFrame -> commonVersionFrame instanceof SiteFrame || commonVersionFrame instanceof CompositeFrame)
                .flatMap(PublicationDeliveryHelper::resolveSiteFramesFromCommonFrame);
    }

    public static Stream<SiteFrame> resolveSiteFramesFromCommonFrame(Common_VersionFrameStructure commonVersionFrame) {
        if (commonVersionFrame instanceof SiteFrame) {
            return Stream.of((SiteFrame) commonVersionFrame);
        } else if (commonVersionFrame instanceof CompositeFrame){
            return ((CompositeFrame) commonVersionFrame).getFrames().getCommonFrame().stream()
                    .map(JAXBElement::getValue)
                    .filter(commonFrame -> commonFrame instanceof SiteFrame)
                    .map(commonFrame -> (SiteFrame) commonFrame);
        } else return Stream.empty();
    }

    public static PublicationDeliveryStructure unmarshall(InputStream in) throws Exception {
        JAXBContext publicationDeliveryContext = newInstance(PublicationDeliveryStructure.class);
        Unmarshaller unmarshaller = publicationDeliveryContext.createUnmarshaller();
        JAXBElement<PublicationDeliveryStructure> jaxbElement = unmarshaller.unmarshal(new StreamSource(in), PublicationDeliveryStructure.class);
        return jaxbElement.getValue();
    }
}
