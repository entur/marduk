package no.rutebanken.marduk.routes.google;

import no.rutebanken.marduk.routes.file.beans.CustomGtfsFileTransformer;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.onebusaway.gtfs_transformer.GtfsTransformer;
import org.onebusaway.gtfs_transformer.services.EntityTransformStrategy;
import org.onebusaway.gtfs_transformer.services.TransformContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;

import static no.rutebanken.marduk.routes.file.GtfsFileUtils.createEntitiesTransformStrategy;

/**
 * For transforming GTFS files to Google.
 */
@Service
public class GoogleGtfsTransformationService {

    private static Logger logger = LoggerFactory.getLogger(GoogleGtfsTransformationService.class);

    private GoogleGtfsFileTransformer TRANSFORMER = new GoogleGtfsFileTransformer();

    /**
     * Google does not support (all) values in the Extended Route Types code set.
     * <p>
     * GTFS to Google needs to be "dumbed" down to the basic code set first.
     */
    public File transformToGoogleFormat(File inputFile) throws Exception {
        long t1 = System.currentTimeMillis();
        logger.debug("Replacing id separator in inputfile: " + inputFile.getPath());

        File outputFile = TRANSFORMER.transform(inputFile);

        logger.debug("Replaced Extended Route Types with basic values in GTFS-file - spent {} ms", (System.currentTimeMillis() - t1));

        return outputFile;
    }


    private class GoogleGtfsFileTransformer extends CustomGtfsFileTransformer {
        @Override
        protected void addCustomTransformations(GtfsTransformer transformer) {
            transformer.addTransform(createEntitiesTransformStrategy(Route.class, new ExtendedRouteTypeTransformer()));
            transformer.addTransform(createEntitiesTransformStrategy(Stop.class, new ExtendedRouteTypeTransformer()));
        }
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
            if (extendedType <= 0) {
                return extendedType;
            }

            return GoogleRouteTypeCode.toGoogleSupportedRouteTypeCode(extendedType);
        }
    }

}
