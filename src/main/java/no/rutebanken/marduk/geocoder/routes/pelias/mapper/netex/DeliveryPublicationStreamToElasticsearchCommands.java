package no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex;

import no.rutebanken.marduk.exceptions.FileValidationException;
import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex.boost.StopPlaceBoostConfiguration;
import org.apache.commons.collections.CollectionUtils;
import org.rutebanken.netex.model.Common_VersionFrameStructure;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.SiteRefStructure;
import org.rutebanken.netex.model.Site_VersionFrameStructure;
import org.rutebanken.netex.model.StopPlace;
import org.rutebanken.netex.model.TopographicPlace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static javax.xml.bind.JAXBContext.newInstance;

@Service
public class DeliveryPublicationStreamToElasticsearchCommands {


    private StopPlaceBoostConfiguration stopPlaceBoostConfiguration;

    private final long poiBoost;

    public DeliveryPublicationStreamToElasticsearchCommands(@Autowired StopPlaceBoostConfiguration stopPlaceBoostConfiguration, @Value("${pelias.poi.boost:1}") long poiBoost) {
        this.stopPlaceBoostConfiguration = stopPlaceBoostConfiguration;
        this.poiBoost = poiBoost;
    }

    public Collection<ElasticsearchCommand> transform(InputStream publicationDeliveryStream) {
        try {
            PublicationDeliveryStructure deliveryStructure = unmarshall(publicationDeliveryStream);
            return fromDeliveryPublicationStructure(deliveryStructure);
        } catch (Exception e) {
            throw new FileValidationException("Parsing of DeliveryPublications failed: " + e.getMessage(), e);
        }
    }


    Collection<ElasticsearchCommand> fromDeliveryPublicationStructure(PublicationDeliveryStructure deliveryStructure) {
        List<ElasticsearchCommand> commands = new ArrayList<>();

        for (JAXBElement<? extends Common_VersionFrameStructure> frameStructureElmt : deliveryStructure.getDataObjects().getCompositeFrameOrCommonFrame()) {
            Common_VersionFrameStructure frameStructure = frameStructureElmt.getValue();
            if (frameStructure instanceof Site_VersionFrameStructure) {
                Site_VersionFrameStructure siteFrame = (Site_VersionFrameStructure) frameStructure;

                if (siteFrame.getStopPlaces() != null) {
                    commands.addAll(addStopPlaceCommands(siteFrame.getStopPlaces().getStopPlace(), deliveryStructure.getParticipantRef()));
                }
                if (siteFrame.getTopographicPlaces() != null) {
                    commands.addAll(addTopographicPlaceCommands(siteFrame.getTopographicPlaces().getTopographicPlace(), deliveryStructure.getParticipantRef()));
                }
            }
        }

        return commands;
    }

    private List<ElasticsearchCommand> addTopographicPlaceCommands(List<TopographicPlace> places, String participantRef) {
        if (!CollectionUtils.isEmpty(places)) {
            TopographicPlaceToPeliasMapper mapper = new TopographicPlaceToPeliasMapper(participantRef, poiBoost);
            return places.stream().map(p -> mapper.toPeliasDocument(new PlaceHierarchy<TopographicPlace>(p))).sorted(new PeliasDocumentPopularityComparator()).filter(d -> d != null).map(p -> ElasticsearchCommand.peliasIndexCommand(p)).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private List<ElasticsearchCommand> addStopPlaceCommands(List<StopPlace> places, String participantRef) {
        if (!CollectionUtils.isEmpty(places)) {
            StopPlaceToPeliasMapper mapper = new StopPlaceToPeliasMapper(participantRef, stopPlaceBoostConfiguration);

            List<PlaceHierarchy<StopPlace>> stopPlaceHierarchies = toPlaceHierarchies(places);

            return stopPlaceHierarchies.stream().map(p -> mapper.toPeliasDocument(p)).sorted(new PeliasDocumentPopularityComparator()).filter(d -> d != null).map(p -> ElasticsearchCommand.peliasIndexCommand(p)).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }


    /**
     * Map list of stop places to list of hierarchies.
     */
    protected List<PlaceHierarchy<StopPlace>> toPlaceHierarchies(List<StopPlace> places) {
        Map<String, List<StopPlace>> childrenByParentIdMap = places.stream().filter(sp -> sp.getParentSiteRef() != null).collect(Collectors.groupingBy(sp -> sp.getParentSiteRef().getRef()));
        return places.stream().filter(sp -> sp.getParentSiteRef() == null).map(sp -> createHierarchyForStopPlace(sp, childrenByParentIdMap)).collect(Collectors.toList());
    }


    private PlaceHierarchy<StopPlace> createHierarchyForStopPlace(StopPlace stopPlace, Map<String, List<StopPlace>> childrenByParentIdMap) {
        List<StopPlace> children = childrenByParentIdMap.get(stopPlace.getId());
        List<PlaceHierarchy<StopPlace>> childHierarchies = new ArrayList<>();
        if (children != null) {
            childHierarchies = children.stream().map(child -> createHierarchyForStopPlace(child, childrenByParentIdMap)).collect(Collectors.toList());
        }
        return new PlaceHierarchy<>(stopPlace, childHierarchies);
    }


    private PublicationDeliveryStructure unmarshall(InputStream in) throws Exception {
        JAXBContext publicationDeliveryContext = newInstance(PublicationDeliveryStructure.class);
        Unmarshaller unmarshaller = publicationDeliveryContext.createUnmarshaller();
        JAXBElement<PublicationDeliveryStructure> jaxbElement = unmarshaller.unmarshal(new StreamSource(in), PublicationDeliveryStructure.class);
        return jaxbElement.getValue();
    }

    private class PeliasDocumentPopularityComparator implements Comparator<PeliasDocument> {

        @Override
        public int compare(PeliasDocument o1, PeliasDocument o2) {
            Long p1 = o1 == null || o1.getPopularity() == null ? 1l : o1.getPopularity();
            Long p2 = o2 == null || o2.getPopularity() == null ? 1l : o2.getPopularity();
            return -p1.compareTo(p2);
        }
    }
}
