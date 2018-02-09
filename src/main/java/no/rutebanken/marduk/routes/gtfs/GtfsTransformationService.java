/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
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
 *
 */

package no.rutebanken.marduk.routes.gtfs;

import no.rutebanken.marduk.routes.file.beans.CustomGtfsFileTransformer;
import no.rutebanken.marduk.routes.google.GoogleRouteTypeCode;
import org.apache.commons.io.IOUtils;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.ShapePoint;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.onebusaway.gtfs_transformer.GtfsTransformer;
import org.onebusaway.gtfs_transformer.factory.EntitiesTransformStrategy;
import org.onebusaway.gtfs_transformer.match.AlwaysMatch;
import org.onebusaway.gtfs_transformer.match.TypedEntityMatch;
import org.onebusaway.gtfs_transformer.services.EntityTransformStrategy;
import org.onebusaway.gtfs_transformer.services.GtfsTransformStrategy;
import org.onebusaway.gtfs_transformer.services.TransformContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;

import static no.rutebanken.marduk.routes.file.GtfsFileUtils.createEntitiesTransformStrategy;

/**
 * For transforming GTFS files.
 */
@Service
public class GtfsTransformationService {

    private static Logger logger = LoggerFactory.getLogger(GtfsTransformationService.class);

    private final GoogleGtfsFileTransformer GOOGLE_TRANSFORMER = new GoogleGtfsFileTransformer();

    private final BasicGtfsFileTransformer BASIC_TRANSFORMER = new BasicGtfsFileTransformer();


    /**
     * Google does not support (all) values in the Extended Route Types code set.
     * <p>
     * GTFS to Google needs to be "dumbed" down to the google supported code set first.
     */
    public File transformToGoogleFormat(File inputFile) throws Exception {
        long t1 = System.currentTimeMillis();
        // Add feed info for google export
        byte[] feedBytes = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("no/rutebanken/marduk/routes/google/feed_info.txt"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream(feedBytes.length);
        baos.write(feedBytes, 0, feedBytes.length);
        File outputFile = GOOGLE_TRANSFORMER.transform(inputFile, baos);

        logger.debug("Replaced Extended Route Types with google supported values in GTFS-file - spent {} ms", (System.currentTimeMillis() - t1));

        return outputFile;
    }

    /**
     * Entur gtfs contains fields and values proposed as extensions to the GTFS standard.
     */
    public File transformToBasicGTFSFormat(File inputFile) throws Exception {
        long t1 = System.currentTimeMillis();
        // Add feed info export
        byte[] feedBytes = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("no/rutebanken/marduk/routes/google/feed_info.txt"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream(feedBytes.length);
        baos.write(feedBytes, 0, feedBytes.length);
        File outputFile = BASIC_TRANSFORMER.transform(inputFile, baos);

        logger.debug("Replaced Extended Route Types with basic values in GTFS-file - spent {} ms", (System.currentTimeMillis() - t1));

        return outputFile;
    }


    private class GoogleGtfsFileTransformer extends CustomGtfsFileTransformer {
        @Override
        protected void addCustomTransformations(GtfsTransformer transformer) {
            transformer.addTransform(new BasicExtendedRouteTypeTransformer.RemoveShapeTransformer());
            transformer.addTransform(createRemoveTripShapeIdStrategy());
            transformer.addTransform(createEntitiesTransformStrategy(Route.class, new GoogleExtendedRouteTypeTransformer()));
            transformer.addTransform(createEntitiesTransformStrategy(Stop.class, new GoogleExtendedRouteTypeTransformer()));
        }
    }

    private class BasicGtfsFileTransformer extends CustomGtfsFileTransformer {
        @Override
        protected void addCustomTransformations(GtfsTransformer transformer) {
            transformer.addTransform(new BasicExtendedRouteTypeTransformer.RemoveShapeTransformer());
            transformer.addTransform(createRemoveTripShapeIdStrategy());
            transformer.addTransform(createEntitiesTransformStrategy(Route.class, new BasicExtendedRouteTypeTransformer()));
            transformer.addTransform(createEntitiesTransformStrategy(Stop.class, new BasicExtendedRouteTypeTransformer()));
        }
    }

    private EntitiesTransformStrategy createRemoveTripShapeIdStrategy() {
        EntitiesTransformStrategy removeShapeStrategy = new EntitiesTransformStrategy();
        removeShapeStrategy.addModification(new TypedEntityMatch(Trip.class, new AlwaysMatch()), (context, dao, entity) -> ((Trip) entity).setShapeId(null));
        return removeShapeStrategy;
    }

    /**
     * "Dumb" down route type code set used for routes and stop points to something that google supports.
     */
    private static class GoogleExtendedRouteTypeTransformer implements EntityTransformStrategy {
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

    /**
     * "Dumb" down route type code set used for routes and stop points to the values in the GTFS specification.
     */
    private static class BasicExtendedRouteTypeTransformer implements EntityTransformStrategy {


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
                return extendedType; // Probably not set
            }
            if (extendedType >= 0 && extendedType <= 7) {
                return extendedType; // Is actually basic type
            }
            if (extendedType >= 100 && extendedType < 200) { // Railway Service
                return BasicRouteTypeCode.RAIL.getCode();
            } else if (extendedType >= 200 && extendedType < 300) { //Coach Service
                return BasicRouteTypeCode.BUS.getCode();
            } else if (extendedType >= 300
                               && extendedType < 500) { //Suburban Railway Service and Urban Railway service
                if (extendedType >= 401 && extendedType <= 402) {
                    return BasicRouteTypeCode.SUBWAY.getCode();
                }
                return BasicRouteTypeCode.RAIL.getCode();
            } else if (extendedType >= 500 && extendedType < 700) {
                return BasicRouteTypeCode.SUBWAY.getCode();
            } else if (extendedType >= 700 && extendedType < 900) {
                return BasicRouteTypeCode.BUS.getCode();
            } else if (extendedType >= 900 && extendedType < 1000) {
                return BasicRouteTypeCode.TRAM.getCode();
            } else if (extendedType >= 1000 && extendedType < 1100) {
                return BasicRouteTypeCode.FERRY.getCode();
            } else if (extendedType >= 1200 && extendedType < 1300) {
                return BasicRouteTypeCode.FERRY.getCode();
            } else if (extendedType >= 1300 && extendedType < 1400) {
                return BasicRouteTypeCode.GONDOLA.getCode();
            } else if (extendedType >= 1400 && extendedType < 1500) {
                return BasicRouteTypeCode.FUNICULAR.getCode();
            }

            logger.warn("Attempted to map unsupported extend route type to basic GTFS route type: {}. Using BUS as default. ", extendedType);
            return BasicRouteTypeCode.BUS.getCode();
        }

        private static class RemoveShapeTransformer implements GtfsTransformStrategy {

            @Override
            public void run(TransformContext context, GtfsMutableRelationalDao dao) {
                dao.clearAllEntitiesForType(ShapePoint.class);
            }

        }
    }

}
