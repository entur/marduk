package no.rutebanken.marduk.geocoder.netex.pbf;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import net.opengis.gml._3.PolygonType;
import no.rutebanken.marduk.geocoder.netex.NetexGeoUtil;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceNetexWriter;
import org.apache.commons.lang3.StringUtils;
import org.opentripplanner.openstreetmap.model.OSMNode;
import org.opentripplanner.openstreetmap.model.OSMRelation;
import org.opentripplanner.openstreetmap.model.OSMWay;
import org.opentripplanner.openstreetmap.model.OSMWithTags;
import org.opentripplanner.openstreetmap.services.OpenStreetMapContentHandler;
import org.rutebanken.netex.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

/**
 * Map OSM nodes and ways to Netex topographic place.
 * <p>
 * Ways refer to nodes for coordinates. Because of this files must be parsed twice,
 * first to collect nodes ids referred by relevant ways and then to map relevant nodes and ways.
 */
public class TopographicPlaceOsmContentHandler implements OpenStreetMapContentHandler {
	private static final Logger logger = LoggerFactory.getLogger(TopographicPlaceNetexWriter.class);

	private BlockingQueue<TopographicPlace> topographicPlaceQueue;

	private List<String> tagFilters;

	private String participantRef;

	private IanaCountryTldEnumeration countryRef;

	private static final String TAG_NAME = "name";

	private Map<Long, OSMNode> nodes = new HashMap<>();

	private Set<Long> nodeRefsUsedInWays = new HashSet<>();

	private boolean gatherNodesUsedInWaysPhase = true;

	public TopographicPlaceOsmContentHandler(BlockingQueue<TopographicPlace> topographicPlaceQueue,
			                                        List<String> tagFilters, String participantRef, IanaCountryTldEnumeration countryRef) {
		this.topographicPlaceQueue = topographicPlaceQueue;
		this.tagFilters = cleanFilter(tagFilters);
		this.participantRef = participantRef;
		this.countryRef = countryRef;
	}

	private List<String> cleanFilter(List<String> rawFilter) {
		if (CollectionUtils.isEmpty(rawFilter)) {
			return new ArrayList<>();
		}
		return rawFilter.stream().filter(f -> !StringUtils.isEmpty(f)).map(String::trim).collect(Collectors.toList());
	}

	@Override
	public void addNode(OSMNode osmNode) {
		if (matchesFilter(osmNode)) {
			TopographicPlace topographicPlace = map(osmNode).withCentroid(toCentroid(osmNode.lat, osmNode.lon));
			topographicPlaceQueue.add(topographicPlace);
		}

		if (nodeRefsUsedInWays.contains(osmNode.getId())) {
			nodes.put(osmNode.getId(), osmNode);
		}
	}

	@Override
	public void addWay(OSMWay osmWay) {
		if (matchesFilter(osmWay)) {

			if (gatherNodesUsedInWaysPhase) {
				nodeRefsUsedInWays.addAll(osmWay.getNodeRefs());
			} else {
				TopographicPlace topographicPlace = map(osmWay);
				if (addGeometry(osmWay, topographicPlace)) {
					topographicPlaceQueue.add(topographicPlace);
				}
			}
		}
	}

	private boolean addGeometry(OSMWay osmWay, TopographicPlace topographicPlace) {
		List<Coordinate> coordinates = new ArrayList<>();
		for (Long nodeRef : osmWay.getNodeRefs()) {
			OSMNode node = nodes.get(nodeRef);
			if (node != null) {
				coordinates.add(new Coordinate(node.lon, node.lat));
			}
		}

		if (coordinates.size() != osmWay.getNodeRefs().size()) {
			logger.info("Ignoring osmWay with missing nodes: " + osmWay.getAssumedName());
			return false;
		}
		topographicPlace.withCentroid(toCentroid(coordinates));
		try {
			topographicPlace.withPolygon(toPolygon(coordinates, participantRef + "-" + osmWay.getId()));
		} catch (RuntimeException e) {
			logger.info("Could not create polygon for osm way: " + osmWay.getAssumedName() + ". Exception: " + e.getMessage());
		}
		return true;
	}

	@Override
	public void doneSecondPhaseWays() {
		gatherNodesUsedInWaysPhase = false;
	}

	boolean matchesFilter(OSMWithTags entity) {
		if (!entity.hasTag(TAG_NAME)) {
			return false;
		}

		for (Map.Entry<String, String> tag : entity.getTags().entrySet()) {
			if (tagFilters.stream().anyMatch(f -> (tag.getKey() + "=" + tag.getValue()).startsWith(f))) {
				return true;
			}
		}
		return false;
	}

	TopographicPlace map(OSMWithTags entity) {
		return new TopographicPlace()
				       .withVersion("any")
				       .withModification(ModificationEnumeration.NEW)
				       .withName(multilingualString(entity.getAssumedName().toString()))
				       .withDescriptor(new TopographicPlaceDescriptor_VersionedChildStructure().withName(multilingualString(entity.getAssumedName().toString())))
				       .withTopographicPlaceType(TopographicPlaceTypeEnumeration.PLACE_OF_INTEREST)
				       .withCountryRef(new CountryRef().withRef(countryRef))
				       .withId(prefix(entity.getId()))
				       .withKeyList(new KeyListStructure().withKeyValue(mapKeyValues(entity)));
	}

	List<KeyValueStructure> mapKeyValues(OSMWithTags entity) {
		return entity.getTags().entrySet().stream()
				       .filter(e -> !TAG_NAME.equals(e.getKey()))
				       .map(e -> new KeyValueStructure().withKey(e.getKey()).withValue(e.getValue()))
				       .collect(Collectors.toList());
	}

	protected String prefix(long id) {
		return participantRef + ":TopographicPlace:" + id;
	}


	protected MultilingualString multilingualString(String val) {
		return new MultilingualString().withLang("no").withValue(val);
	}


	private PolygonType toPolygon(List<Coordinate> coordinates, String id) {
		Polygon polygon = new GeometryFactory().createPolygon(coordinates.toArray(new Coordinate[coordinates.size()]));
		return NetexGeoUtil.toNetexPolygon(polygon).withId(id);
	}


	SimplePoint_VersionStructure toCentroid(List<Coordinate> coordinates) {
		Point centroid = new GeometryFactory().createMultiPoint(coordinates.toArray(new Coordinate[coordinates.size()])).getCentroid();
		return toCentroid(centroid.getX(), centroid.getY());
	}


	SimplePoint_VersionStructure toCentroid(double latitude, double longitude) {
		return new SimplePoint_VersionStructure().withLocation(
				new LocationStructure().withLatitude(new BigDecimal(latitude))
						.withLongitude(new BigDecimal(longitude)));
	}

	@Override
	public void addRelation(OSMRelation osmRelation) {
		// Ignore
	}

	@Override
	public void doneFirstPhaseRelations() {
		// Ignore
	}


	@Override
	public void doneThirdPhaseNodes() {
		// Ignore
	}


}
