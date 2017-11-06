package no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex;

import no.rutebanken.marduk.exceptions.FileValidationException;
import no.rutebanken.marduk.geocoder.routes.pelias.elasticsearch.ElasticsearchCommand;
import no.rutebanken.marduk.geocoder.routes.pelias.json.PeliasDocument;
import no.rutebanken.marduk.geocoder.routes.pelias.mapper.netex.boost.StopPlaceBoostConfiguration;
import org.apache.commons.collections.CollectionUtils;
import org.rutebanken.netex.model.Common_VersionFrameStructure;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.Site_VersionFrameStructure;
import org.rutebanken.netex.model.StopPlace;
import org.rutebanken.netex.model.TopographicPlace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static javax.xml.bind.JAXBContext.newInstance;

@Service
public class DeliveryPublicationStreamToElasticsearchCommands {


    private StopPlaceBoostConfiguration stopPlaceBoostConfiguration;

    private final long poiBoost;

    private final List<String> poiFilter;

    public DeliveryPublicationStreamToElasticsearchCommands(@Autowired StopPlaceBoostConfiguration stopPlaceBoostConfiguration, @Value("${pelias.poi.boost:1}") long poiBoost, @Value("#{'${pelias.poi.filter:}'.split(',')}") List<String> poiFilter) {
        this.stopPlaceBoostConfiguration = stopPlaceBoostConfiguration;
        this.poiBoost = poiBoost;
        if (poiFilter != null) {
            this.poiFilter = poiFilter.stream().filter(filter -> !StringUtils.isEmpty(filter)).collect(Collectors.toList());
        } else {
            this.poiFilter = new ArrayList<>();
        }
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
            TopographicPlaceToPeliasMapper mapper = new TopographicPlaceToPeliasMapper(participantRef, poiBoost, poiFilter);
            return places.stream().map(p -> mapper.toPeliasDocuments(new PlaceHierarchy<TopographicPlace>(p))).flatMap(documents -> documents.stream()).sorted(new PeliasDocumentPopularityComparator()).filter(d -> d != null).map(p -> ElasticsearchCommand.peliasIndexCommand(p)).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private List<ElasticsearchCommand> addStopPlaceCommands(List<StopPlace> places, String participantRef) {
        if (!CollectionUtils.isEmpty(places)) {
            StopPlaceToPeliasMapper mapper = new StopPlaceToPeliasMapper(participantRef, stopPlaceBoostConfiguration);

            Set<PlaceHierarchy<StopPlace>> stopPlaceHierarchies = toPlaceHierarchies(places);

            return stopPlaceHierarchies.stream().map(p -> mapper.toPeliasDocuments(p)).flatMap(documents -> documents.stream()).sorted(new PeliasDocumentPopularityComparator()).map(p -> ElasticsearchCommand.peliasIndexCommand(p)).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }


    private void expandStopPlaceHierarchies(Collection<PlaceHierarchy<StopPlace>> hierarchies, Set<PlaceHierarchy<StopPlace>> target) {
        if (hierarchies != null) {
            for (PlaceHierarchy<StopPlace> stopPlacePlaceHierarchy : hierarchies) {
                target.add(stopPlacePlaceHierarchy);
                expandStopPlaceHierarchies(stopPlacePlaceHierarchy.getChildren(), target);
            }
        }
    }


    /**
     * Map list of stop places to list of hierarchies.
     */
    protected Set<PlaceHierarchy<StopPlace>> toPlaceHierarchies(List<StopPlace> places) {
        Map<String, List<StopPlace>> childrenByParentIdMap = places.stream().filter(sp -> sp.getParentSiteRef() != null).collect(Collectors.groupingBy(sp -> sp.getParentSiteRef().getRef()));
        Set<PlaceHierarchy<StopPlace>> allStopPlaces = new HashSet<>();
        expandStopPlaceHierarchies(places.stream().filter(sp -> sp.getParentSiteRef() == null).map(sp -> createHierarchyForStopPlace(sp, null, childrenByParentIdMap)).collect(Collectors.toList()), allStopPlaces);
        return allStopPlaces;
    }


    private PlaceHierarchy<StopPlace> createHierarchyForStopPlace(StopPlace stopPlace, PlaceHierarchy<StopPlace> parent, Map<String, List<StopPlace>> childrenByParentIdMap) {
        List<StopPlace> children = childrenByParentIdMap.get(stopPlace.getId());
        List<PlaceHierarchy<StopPlace>> childHierarchies = new ArrayList<>();
        PlaceHierarchy<StopPlace> hierarchy = new PlaceHierarchy<>(stopPlace, parent);
        if (children != null) {
            childHierarchies = children.stream().map(child -> createHierarchyForStopPlace(child, hierarchy, childrenByParentIdMap)).collect(Collectors.toList());
        }
        hierarchy.setChildren(childHierarchies);
        return hierarchy;
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
