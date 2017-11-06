package no.rutebanken.marduk.routes.google;

import no.rutebanken.marduk.routes.file.beans.CustomGtfsFileTransformer;
import org.apache.commons.io.IOUtils;
import org.onebusaway.collections.beans.PropertyPathExpression;
import org.onebusaway.gtfs.model.Agency;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.onebusaway.gtfs_transformer.GtfsTransformer;
import org.onebusaway.gtfs_transformer.factory.EntitiesTransformStrategy;
import org.onebusaway.gtfs_transformer.impl.RemoveEntityUpdateStrategy;
import org.onebusaway.gtfs_transformer.match.EntityMatch;
import org.onebusaway.gtfs_transformer.match.PropertyValueEntityMatch;
import org.onebusaway.gtfs_transformer.match.TypedEntityMatch;
import org.onebusaway.gtfs_transformer.services.EntityTransformStrategy;
import org.onebusaway.gtfs_transformer.services.TransformContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Set;

import static no.rutebanken.marduk.routes.file.GtfsFileUtils.createEntitiesTransformStrategy;

/**
 * For transforming GTFS files to Google.
 */
@Service
public class GoogleGtfsTransformationService {

    private static Logger logger = LoggerFactory.getLogger(GoogleGtfsTransformationService.class);

    private final GoogleGtfsFileTransformer TRANSFORMER = new GoogleGtfsFileTransformer();

    private final Set<String> agencyBlackList;

    public GoogleGtfsTransformationService(@Value("#{'${google.export.agency.prefix.blacklist:AVI}'.split(',')}") Set<String> agencyBlackList) {
        this.agencyBlackList = agencyBlackList;
    }

    /**
     * Google does not support (all) values in the Extended Route Types code set.
     * <p>
     * GTFS to Google needs to be "dumbed" down to the basic code set first.
     */
    public File transformToGoogleFormat(File inputFile) throws Exception {
        long t1 = System.currentTimeMillis();
        logger.debug("Replacing id separator in inputfile: " + inputFile.getPath());

        // Add feed info for google export
        byte[] feedBytes=IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("no/rutebanken/marduk/routes/google/feed_info.txt"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream(feedBytes.length);
        baos.write(feedBytes, 0, feedBytes.length);
        File outputFile = TRANSFORMER.transform(inputFile, baos);

        logger.debug("Replaced Extended Route Types with basic values in GTFS-file - spent {} ms", (System.currentTimeMillis() - t1));

        return outputFile;
    }


    private class GoogleGtfsFileTransformer extends CustomGtfsFileTransformer {
        @Override
        protected void addCustomTransformations(GtfsTransformer transformer) {
            transformer.addTransform(createRemoveBlackListedAgenciesTransform());

            transformer.addTransform(createEntitiesTransformStrategy(Route.class, new ExtendedRouteTypeTransformer()));
            transformer.addTransform(createEntitiesTransformStrategy(Stop.class, new ExtendedRouteTypeTransformer()));
        }
    }

    private EntitiesTransformStrategy createRemoveBlackListedAgenciesTransform() {
        EntitiesTransformStrategy agencyFilter = new EntitiesTransformStrategy();
        EntityMatch blackListedAgencyMatcher = new PropertyValueEntityMatch(new PropertyPathExpression("id"), (parentEntityType, propertyName, value) ->
                                                                                                                      agencyBlackList.stream().anyMatch(idPrefix -> value.toString().startsWith(idPrefix)));
        agencyFilter.addModification(new TypedEntityMatch(Agency.class, blackListedAgencyMatcher), new RemoveEntityUpdateStrategy());
        return agencyFilter;
    }


    /**
     * "Dumb" down route type code set used for routes and stop points to something that google supports.
     */
    private static class ExtendedRouteTypeTransformer implements EntityTransformStrategy {
        @Override
        public void run(TransformContext context, GtfsMutableRelationalDao dao, Object entity) {
            if (entity instanceof Route) {
                Route route = (Route) entity;
                route.setType(convertRouteType(route.getType()));
            } else if (entity instanceof Stop) {
                Stop stop = (Stop) entity;
                stop.setVehicleType(convertRouteType(stop.getVehicleType()));
            }
        }

        private int convertRouteType(int extendedType) {
            if (extendedType < 0) {
                return extendedType;
            }

            return GoogleRouteTypeCode.toGoogleSupportedRouteTypeCode(extendedType);
        }
    }

}
